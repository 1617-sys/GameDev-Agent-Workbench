package com.example.gameworkbench.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.client.dto.*;
import com.example.gameworkbench.common.enums.*;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.telemetry.*;
import com.example.gameworkbench.entity.*;
import com.example.gameworkbench.gameconfig.*;
import com.example.gameworkbench.mapper.*;
import com.example.gameworkbench.service.PlaytestTelemetryService;
import com.example.gameworkbench.vo.telemetry.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class PlaytestTelemetryServiceImpl implements PlaytestTelemetryService {
    private static final Set<String> TYPES=Set.of("SESSION_STARTED","ITEM_COLLECTED","PLAYER_HIT","GAME_WON","GAME_LOST","SESSION_RESTARTED","SESSION_ENDED");
    private static final Set<String> LOST=Set.of("HEALTH_DEPLETED","TIME_EXPIRED");
    private static final Set<String> ENDED=Set.of("COMPLETED","USER_EXIT","PAGE_HIDDEN","TIMEOUT");
    private static final Pattern KEY=Pattern.compile("^[A-Za-z0-9._~-]{1,128}$");
    private final GameProjectMapper projects; private final PrototypeVersionMapper versions; private final AgentArtifactMapper artifacts;
    private final PlaytestSessionMapper sessions; private final PlaytestEventMapper events; private final PlaytestEventBatchMapper batches;
    private final PrototypePlaytestAggregateMapper aggregates; private final BalanceSuggestionRequestMapper suggestionRequests;
    private final PythonAgentClient python; private final ObjectMapper json; private final GameConfigContract gameConfigs;

    @Override @Transactional
    public PlaytestSessionVO createSession(Long userId,String projectUuid,String versionUuid) {
        GameProject project=ownedProject(userId,projectUuid); PrototypeVersion version=ownedVersion(project.getId(),versionUuid);
        if(sessions.countRecentByUser(userId)>=20) throw new BusinessException(ErrorCode.TELEMETRY_RATE_LIMITED);
        PlaytestSession value=PlaytestSession.builder().sessionUuid(UUID.randomUUID().toString()).userId(userId).projectId(project.getId())
            .prototypeVersionUuid(version.getVersionUuid()).status("ACTIVE").startedAt(LocalDateTime.now()).lastSequence(0).eventCount(0)
            .outcome("NONE").score(0).durationMs(0).hitCount(0).collectedCount(0).restartCount(0).build();
        sessions.insert(value); return sessionVO(value);
    }

    @Override @Transactional
    public TelemetryBatchVO ingest(Long userId,String projectUuid,String sessionUuid,TelemetryBatchRequest request,int requestBytes) {
        if(requestBytes>65536) throw new BusinessException(ErrorCode.TELEMETRY_TOO_LARGE);
        GameProject project=ownedProject(userId,projectUuid); PlaytestSession session=sessions.lockByUuid(sessionUuid);
        if(session==null) throw new BusinessException(ErrorCode.PLAYTEST_SESSION_NOT_FOUND);
        if(!Objects.equals(session.getUserId(),userId)||!Objects.equals(session.getProjectId(),project.getId())) throw new BusinessException(ErrorCode.FORBIDDEN_PLAYTEST_ACCESS);
        expireIfNeeded(session);
        if(batches.countRecentByUser(userId)>=60||batches.countRecentBySession(session.getId())>=30) throw new BusinessException(ErrorCode.TELEMETRY_RATE_LIMITED);
        String batchDigest=canonicalDigest(request);
        PlaytestEventBatch replay=batches.selectBatch(session.getId(),request.getBatchUuid());
        if(replay!=null) {
            if(!replay.getBatchDigest().equals(batchDigest)) throw new BusinessException(ErrorCode.TELEMETRY_IDEMPOTENCY_CONFLICT);
            return TelemetryBatchVO.builder().batchUuid(replay.getBatchUuid()).acceptedCount(replay.getAcceptedCount()).reused(true).session(sessionVO(session)).build();
        }
        List<TelemetryEventRequest> ordered=new ArrayList<>(request.getEvents()); ordered.sort(Comparator.comparing(TelemetryEventRequest::getSequence));
        validateBatchShape(ordered);
        List<PlaytestEvent> fresh=new ArrayList<>();
        for(TelemetryEventRequest input:ordered) {
            validatePayload(input,config(session)); String digest=canonicalDigest(input);
            PlaytestEvent byUuid=events.selectByUuid(input.getEventUuid()); PlaytestEvent bySeq=events.selectBySequence(session.getId(),input.getSequence());
            if(byUuid!=null||bySeq!=null) {
                PlaytestEvent same=byUuid!=null?byUuid:bySeq;
                if(!Objects.equals(same.getSessionId(),session.getId())||!same.getEventDigest().equals(digest)) throw new BusinessException(ErrorCode.TELEMETRY_IDEMPOTENCY_CONFLICT);
                continue;
            }
            if("ENDED".equals(session.getStatus()) && (session.getCloseAfter()==null||LocalDateTime.now().isAfter(session.getCloseAfter())
                    || input.getSequence()>=session.getLastSequence())) throw new BusinessException(ErrorCode.PLAYTEST_SESSION_CLOSED);
            fresh.add(PlaytestEvent.builder().eventUuid(input.getEventUuid()).sessionId(session.getId()).sequenceNumber(input.getSequence())
                .eventType(input.getType()).clientElapsedMs(input.getClientElapsedMs()).payloadJson(write(input.getPayload()))
                .eventDigest(digest).receivedAt(LocalDateTime.now()).build());
        }
        if(session.getEventCount()+fresh.size()>1000) throw new BusinessException(ErrorCode.TELEMETRY_INVALID);
        validateTimeline(session.getId(),fresh);
        try { fresh.forEach(events::insert); } catch(DuplicateKeyException conflict) { throw new BusinessException(ErrorCode.TELEMETRY_IDEMPOTENCY_CONFLICT); }
        recomputeSession(session,events.selectSessionEvents(session.getId()),config(session)); sessions.updateById(session);
        PlaytestEventBatch batch=PlaytestEventBatch.builder().sessionId(session.getId()).batchUuid(request.getBatchUuid()).batchDigest(batchDigest)
            .acceptedCount(fresh.size()).createdAt(LocalDateTime.now()).build(); batches.insert(batch);
        if("ENDED".equals(session.getStatus())) aggregates.recompute(session.getPrototypeVersionUuid());
        return TelemetryBatchVO.builder().batchUuid(request.getBatchUuid()).acceptedCount(fresh.size()).reused(false).session(sessionVO(session)).build();
    }

    @Override public PlaytestSessionVO getSession(Long userId,String projectUuid,String uuid) {
        GameProject p=ownedProject(userId,projectUuid); PlaytestSession s=sessions.selectByUuid(uuid);
        if(s==null) throw new BusinessException(ErrorCode.PLAYTEST_SESSION_NOT_FOUND);
        if(!s.getProjectId().equals(p.getId())||!s.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN_PLAYTEST_ACCESS);
        return sessionVO(s);
    }
    @Override public PlaytestMetricsVO metrics(Long userId,String projectUuid,String versionUuid) {
        GameProject p=ownedProject(userId,projectUuid); ownedVersion(p.getId(),versionUuid); PrototypePlaytestAggregate a=aggregates.selectByVersion(versionUuid);
        return metricVO(versionUuid,a);
    }
    @Override public PlaytestMetricsComparisonVO compare(Long userId,String projectUuid,String left,String right) {
        return PlaytestMetricsComparisonVO.builder().left(metrics(userId,projectUuid,left)).right(metrics(userId,projectUuid,right)).build();
    }

    @Override @Transactional
    public BalanceSuggestionVO suggest(Long userId,String projectUuid,String versionUuid,String key) {
        if(key==null||!KEY.matcher(key).matches()) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        GameProject p=ownedProject(userId,projectUuid); PrototypeVersion v=ownedVersion(p.getId(),versionUuid); PlaytestMetricsVO m=metrics(userId,projectUuid,versionUuid);
        if(!m.isSufficientForAi()) throw new BusinessException(ErrorCode.PLAYTEST_SAMPLE_INSUFFICIENT);
        String fingerprint=digest(v.getConfigDigest()+":"+m.getSnapshotAt()+":"+m.getSampleSize());
        BalanceSuggestionRequest replay=suggestionRequests.selectIdempotent(userId,p.getId(),key);
        if(replay!=null) {
            if(!replay.getRequestFingerprint().equals(fingerprint)) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            return suggestionVO(artifacts.selectByArtifactUuid(replay.getArtifactUuid()),v,m,true);
        }
        AgentArtifact config=artifacts.selectByArtifactUuid(v.getGameConfigArtifactUuid());
        PythonAgentResponse response=python.invoke(AgentType.BALANCE_EVALUATION,PythonAgentRequest.builder().projectUuid(projectUuid)
            .title("Balance evaluation for "+versionUuid).content(config.getContent()).context(write(m)).build());
        String recommendation=response.getData().path("output").path("content").asText();
        if(recommendation.isBlank()||recommendation.length()>5000) throw new BusinessException(ErrorCode.PYTHON_INVALID_RESPONSE);
        ObjectNode body=json.createObjectNode(); body.put("schemaVersion","1.0"); body.put("prototypeVersionUuid",versionUuid);
        body.put("configDigest",v.getConfigDigest()); body.put("sampleSize",m.getSampleSize()); body.put("snapshotAt",m.getSnapshotAt().toString());
        body.put("source","AI_BALANCE_EVALUATION"); body.put("recommendation",recommendation); String content=canonical(body);
        AgentArtifact artifact=AgentArtifact.builder().artifactUuid(UUID.randomUUID().toString()).projectId(p.getId()).artifactType("BALANCE_SUGGESTION")
            .title("Balance suggestion for version "+v.getVersionNumber()).content(content).contentDigest(digest(content)).schemaKey("balance-suggestion")
            .schemaVersion("1.0").validationSummary("sampleSize="+m.getSampleSize()).sourceAttempt(1).sourceArtifactUuid(config.getArtifactUuid())
            .runtimeEligible(false).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).deleted(0).build(); artifacts.insert(artifact);
        suggestionRequests.insert(BalanceSuggestionRequest.builder().userId(userId).projectId(p.getId()).prototypeVersionUuid(versionUuid)
            .idempotencyKey(key).requestFingerprint(fingerprint).artifactUuid(artifact.getArtifactUuid()).createdAt(LocalDateTime.now()).build());
        return suggestionVO(artifact,v,m,false);
    }

    private void validateBatchShape(List<TelemetryEventRequest> list) {
        Set<String> uuids=new HashSet<>(); Set<Integer> sequences=new HashSet<>();
        for(TelemetryEventRequest e:list) if(!uuids.add(e.getEventUuid())||!sequences.add(e.getSequence())||!TYPES.contains(e.getType())) throw new BusinessException(ErrorCode.TELEMETRY_INVALID);
    }
    private void validatePayload(TelemetryEventRequest e,JsonNode config) {
        Map<String,Object> p=e.getPayload();
        switch(e.getType()) {
            case "SESSION_STARTED","GAME_WON","SESSION_RESTARTED" -> requireKeys(p,Set.of());
            case "ITEM_COLLECTED" -> { requireKeys(p,Set.of("itemId")); String id=string(p.get("itemId"));
                if(!containsId(config.path("entities").path("collectibles"),id)) invalid(); }
            case "PLAYER_HIT" -> { requireKeys(p,Set.of("enemyId")); String id=string(p.get("enemyId"));
                if(!containsId(config.path("entities").path("enemies"),id)) invalid(); }
            case "GAME_LOST" -> { requireKeys(p,Set.of("reason")); if(!LOST.contains(string(p.get("reason")))) invalid(); }
            case "SESSION_ENDED" -> { requireKeys(p,Set.of("reason")); if(!ENDED.contains(string(p.get("reason")))) invalid(); }
            default -> invalid();
        }
        if("SESSION_STARTED".equals(e.getType())&&(e.getSequence()!=1||e.getClientElapsedMs()!=0)) invalid();
    }
    private void validateTimeline(Long sessionId,List<PlaytestEvent> fresh) {
        List<PlaytestEvent> all=new ArrayList<>(events.selectSessionEvents(sessionId)); all.addAll(fresh); all.sort(Comparator.comparing(PlaytestEvent::getSequenceNumber));
        for(int i=1;i<all.size();i++) if(all.get(i).getClientElapsedMs()<all.get(i-1).getClientElapsedMs()) invalid();
    }
    private void recomputeSession(PlaytestSession s,List<PlaytestEvent> all,JsonNode config) {
        all.sort(Comparator.comparing(PlaytestEvent::getSequenceNumber)); int totalHits=0,totalItems=0,restarts=0,score=0;
        if(!all.isEmpty()&&all.get(0).getSequenceNumber()==1&&!"SESSION_STARTED".equals(all.get(0).getEventType())) invalid();
        List<PlaytestEvent> ending=all.stream().filter(e->"SESSION_ENDED".equals(e.getEventType())).toList();
        if(ending.size()>1||(!ending.isEmpty()&&!Objects.equals(ending.get(0).getSequenceNumber(),all.get(all.size()-1).getSequenceNumber()))) invalid();
        int health=config.path("player").path("maxHealth").asInt(); int damage=config.path("behaviors").path("contact").path("damage").asInt();
        int target=config.path("objectives").path("targetCollectibles").asInt(); int invul=config.path("player").path("hitInvulnerabilityMs").asInt();
        int invulnerableUntil=-1; Set<String> attemptItems=new HashSet<>(); String outcome="NONE",failure=null; boolean contiguous=true; int expected=1;
        Map<String,Integer> itemScores=new HashMap<>(); config.path("entities").path("collectibles").forEach(n->itemScores.put(n.path("id").asText(),n.path("score").asInt()));
        for(PlaytestEvent e:all) {
            if(e.getSequenceNumber()!=expected) contiguous=false; expected=e.getSequenceNumber()+1; JsonNode p=read(e.getPayloadJson());
            switch(e.getEventType()) {
                case "ITEM_COLLECTED" -> { String id=p.path("itemId").asText(); if(attemptItems.add(id)){score+=itemScores.getOrDefault(id,0);totalItems++;} }
                case "PLAYER_HIT" -> { if(e.getClientElapsedMs()>=invulnerableUntil){totalHits++;health=Math.max(0,health-damage);invulnerableUntil=e.getClientElapsedMs()+invul;} }
                case "GAME_WON" -> { if(contiguous&&attemptItems.size()<target) invalid(); outcome="WON";failure=null;score+=config.path("balance").path("winBonus").asInt(); }
                case "GAME_LOST" -> { String reason=p.path("reason").asText(); if(contiguous&&"HEALTH_DEPLETED".equals(reason)&&health>0) invalid(); outcome="LOST";failure=reason; }
                case "SESSION_RESTARTED" -> { restarts++;attemptItems.clear();score=0;health=config.path("player").path("maxHealth").asInt();invulnerableUntil=-1;outcome="NONE";failure=null; }
                case "SESSION_ENDED" -> { s.setStatus("ENDED");if(s.getEndedAt()==null){s.setEndedAt(LocalDateTime.now());s.setCloseAfter(LocalDateTime.now().plusSeconds(60));}if("NONE".equals(outcome))outcome="ABANDONED"; }
                default -> { }
            }
        }
        s.setLastSequence(all.stream().mapToInt(PlaytestEvent::getSequenceNumber).max().orElse(0));s.setEventCount(all.size());s.setOutcome(outcome);s.setFailureReason(failure);
        s.setScore(score);s.setDurationMs(all.stream().mapToInt(PlaytestEvent::getClientElapsedMs).max().orElse(0));s.setHitCount(totalHits);s.setCollectedCount(totalItems);s.setRestartCount(restarts);
    }
    private void expireIfNeeded(PlaytestSession s) { if("ACTIVE".equals(s.getStatus())&&s.getStartedAt().plusMinutes(30).isBefore(LocalDateTime.now())) { s.setStatus("ENDED");s.setEndedAt(LocalDateTime.now());s.setOutcome("ABANDONED");s.setCloseAfter(LocalDateTime.now());sessions.updateById(s);aggregates.recompute(s.getPrototypeVersionUuid());throw new BusinessException(ErrorCode.PLAYTEST_SESSION_CLOSED); } }
    private JsonNode config(PlaytestSession s) { PrototypeVersion v=versions.selectByUuid(s.getPrototypeVersionUuid());AgentArtifact a=artifacts.selectByArtifactUuid(v.getGameConfigArtifactUuid());GameConfigContractResult r=gameConfigs.process(a.getContent());if(!r.valid())throw new IllegalStateException("Version config invalid");return r.canonicalConfig(); }
    private GameProject ownedProject(Long userId,String uuid){ if(userId==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);GameProject p=projects.selectOne(new LambdaQueryWrapper<GameProject>().eq(GameProject::getProjectUuid,uuid).eq(GameProject::getUserId,userId));if(p==null)throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);return p; }
    private PrototypeVersion ownedVersion(Long projectId,String uuid){PrototypeVersion v=versions.selectByUuid(uuid);if(v==null)throw new BusinessException(ErrorCode.PROTOTYPE_VERSION_NOT_FOUND);if(!v.getProjectId().equals(projectId))throw new BusinessException(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS);return v;}
    private PlaytestMetricsVO metricVO(String uuid,PrototypePlaytestAggregate a){int n=a==null?0:a.getEndedSessionCount();return PlaytestMetricsVO.builder().prototypeVersionUuid(uuid).sampleSize(n).sufficientForAi(n>=5)
        .winRate(n==0?0d:a.getWonCount()/(double)n).averageDurationMs(n==0?0:a.getTotalDurationMs()/n).averageScore(n==0?0:a.getTotalScore()/n)
        .averageHitCount(n==0?0d:a.getTotalHitCount()/(double)n).averageCollectedCount(n==0?0d:a.getTotalCollectedCount()/(double)n)
        .averageRestartCount(n==0?0d:a.getTotalRestartCount()/(double)n).failures(Map.of("HEALTH_DEPLETED",a==null?0:a.getHealthDepletedCount(),"TIME_EXPIRED",a==null?0:a.getTimeExpiredCount(),"ABANDONED",a==null?0:a.getAbandonedCount()))
        .snapshotAt(a==null?null:a.getSnapshotAt()).build();}
    private PlaytestSessionVO sessionVO(PlaytestSession s){return PlaytestSessionVO.builder().sessionUuid(s.getSessionUuid()).prototypeVersionUuid(s.getPrototypeVersionUuid()).status(s.getStatus()).startedAt(s.getStartedAt()).endedAt(s.getEndedAt()).eventCount(s.getEventCount()).outcome(s.getOutcome()).failureReason(s.getFailureReason()).score(s.getScore()).durationMs(s.getDurationMs()).hitCount(s.getHitCount()).collectedCount(s.getCollectedCount()).restartCount(s.getRestartCount()).build();}
    private BalanceSuggestionVO suggestionVO(AgentArtifact a,PrototypeVersion v,PlaytestMetricsVO m,boolean reused){JsonNode body=read(a.getContent());return BalanceSuggestionVO.builder().artifactUuid(a.getArtifactUuid()).prototypeVersionUuid(v.getVersionUuid()).configDigest(v.getConfigDigest()).sampleSize(m.getSampleSize()).snapshotAt(m.getSnapshotAt()).source(body.path("source").asText()).recommendation(body.path("recommendation").asText()).reused(reused).build();}
    private void requireKeys(Map<String,Object> p,Set<String> keys){if(!p.keySet().equals(keys))invalid();} private String string(Object v){if(!(v instanceof String s)||s.isBlank()||s.length()>64)invalid();return (String)v;}
    private boolean containsId(JsonNode a,String id){for(JsonNode n:a)if(id.equals(n.path("id").asText()))return true;return false;} private void invalid(){throw new BusinessException(ErrorCode.TELEMETRY_INVALID);}
    private String canonicalDigest(Object v){JsonNode n=json.valueToTree(v);return digest(canonical((ObjectNode)n));} private String canonical(ObjectNode n){return gameConfigs.canonicalJson(n);} private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}} private JsonNode read(String v){try{return json.readTree(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private String digest(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
