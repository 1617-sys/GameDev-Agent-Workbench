package com.example.gameworkbench.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.player.CreatePlayerRunRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.example.gameworkbench.service.PlayerRunService;
import com.example.gameworkbench.vo.player.PlayerRunVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlayerRunServiceImpl implements PlayerRunService {
    private static final String DETERMINISTIC_DIGEST="40071122c94fe87791fab1f7b244a4d463a15618b0783555fc7abcf00f7c3964";
    private static final String LLM_DIGEST="11a465981fec5c82a35446026cfaebcfa062868be19a9735dbda007310bed0f7";
    private static final Map<String,String> PERSONA_DIGESTS=Map.of(
            "baseline-neutral","3499eb9a21da21944ad5a0a79fb4da0261202feeadb00968b67eea1dbd8ebb32",
            "NOVICE","315d16d1175a1099e3c617f8432821bd8fd79a0be84548ce71bf49f64f213c6c",
            "REGULAR","0c6ebc23d26d415a5ed9798b66da681c55ce0ee0aa749a149da95237db3d8d49",
            "EXPERT","2cf718403729f8dd2424aa92b2d6517bbd048b4db140f19289cdc0afdb0ebdc9");
    private static final Map<String,Integer> VISION=Map.of("NOVICE",160,"REGULAR",320,"EXPERT",640);
    private final GameProjectMapper projects;
    private final PrototypeVersionMapper versions;
    private final AgentArtifactMapper artifacts;
    private final PlayerRunMapper runs;
    private final GameConfigContract gameConfigs;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    @Value("${app.player.model:deepseek-chat}") private String playerModel;

    @Override @Transactional
    public PlayerRunVO submit(Long userId,String projectUuid,String idempotencyKey,String traceId,CreatePlayerRunRequest request){
        if(idempotencyKey==null||!idempotencyKey.matches("[A-Za-z0-9._:@/-]{8,128}"))throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        GameProject project=ownedProject(userId,projectUuid);
        String fingerprint=digest(write(request));
        PlayerRun replay=runs.selectIdempotent(userId,project.getId(),idempotencyKey);
        if(replay!=null){if(!Objects.equals(fingerprint,replay.getRequestFingerprint()))throw new BusinessException(ErrorCode.PLAYER_RUN_IDEMPOTENCY_CONFLICT);return vo(replay,true);}
        PrototypeVersion version=versions.selectByUuid(request.getPrototypeVersionUuid());
        if(version==null)throw new BusinessException(ErrorCode.PROTOTYPE_VERSION_NOT_FOUND);
        if(!Objects.equals(project.getId(),version.getProjectId()))throw new BusinessException(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS);
        AgentArtifact artifact=artifacts.selectByArtifactUuid(version.getGameConfigArtifactUuid());
        if(artifact==null||!Objects.equals(project.getId(),artifact.getProjectId())||!Objects.equals(version.getConfigDigest(),artifact.getContentDigest()))throw new BusinessException(ErrorCode.EPISODE_BINDING_MISMATCH);
        var validated=gameConfigs.process(artifact.getContent());
        if(!validated.valid())throw new BusinessException(ErrorCode.PLAYER_RUN_INVALID);
        validateItems(request);
        String runUuid=UUID.randomUUID().toString(),batchUuid=UUID.randomUUID().toString(),resolvedTrace=safeTrace(traceId);
        ObjectNode payload=json.createObjectNode();payload.put("episodeProtocolVersion","episode/1.0").put("clientBatchKey",request.getClientBatchKey()).put("concurrency",request.getConcurrency());
        ArrayNode items=payload.putArray("episodes");
        for(CreatePlayerRunRequest.Item item:request.getEpisodes())items.add(episode(projectUuid,version,validated.canonicalConfig(),batchUuid,resolvedTrace,item));
        LocalDateTime now=LocalDateTime.now();
        PlayerRun run=PlayerRun.builder().runUuid(runUuid).userId(userId).projectId(project.getId()).projectUuid(projectUuid)
                .prototypeVersionUuid(version.getVersionUuid()).idempotencyKey(idempotencyKey).requestFingerprint(fingerprint)
                .clientBatchKey(request.getClientBatchKey()).status("PENDING").requestJson(write(payload)).traceId(resolvedTrace)
                .attempt(0).createdAt(now).updatedAt(now).build();
        runs.insert(run);events.publishEvent(new PlayerRunRequested(runUuid));return vo(run,false);
    }

    @Override public PlayerRunVO get(Long userId,String projectUuid,String runUuid){GameProject project=ownedProject(userId,projectUuid);PlayerRun run=runs.selectByUuid(runUuid);if(run==null)throw new BusinessException(ErrorCode.PLAYER_RUN_NOT_FOUND);if(!Objects.equals(project.getId(),run.getProjectId()))throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);return vo(run,false);}
    @Override public List<PlayerRunVO> list(Long userId,String projectUuid,String versionUuid){GameProject project=ownedProject(userId,projectUuid);PrototypeVersion version=versions.selectByUuid(versionUuid);if(version==null)throw new BusinessException(ErrorCode.PROTOTYPE_VERSION_NOT_FOUND);if(!Objects.equals(project.getId(),version.getProjectId()))throw new BusinessException(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS);return runs.selectVersionRuns(project.getId(),versionUuid,100).stream().map(run->vo(run,false)).toList();}

    private ObjectNode episode(String projectUuid,PrototypeVersion version,ObjectNode config,String batchUuid,String traceId,CreatePlayerRunRequest.Item item){
        String persona=item.getPersonaId(),kind=item.getPolicyKind();ObjectNode root=json.createObjectNode();
        root.put("episodeProtocolVersion","episode/1.0").put("episodeId",UUID.randomUUID().toString()).put("batchId",batchUuid)
                .put("clientEpisodeKey",item.getClientEpisodeKey()).put("correlationId",safeTrace(traceId));
        root.putObject("prototype").put("projectUuid",projectUuid).put("prototypeVersionUuid",version.getVersionUuid()).put("gameConfigArtifactUuid",version.getGameConfigArtifactUuid()).put("configDigest",version.getConfigDigest()).put("gameConfigSchemaVersion","game-config/2.0").put("runtimeCapabilityVersion",version.getRuntimeCapabilityVersion());
        ObjectNode simulation=root.putObject("simulation");simulation.put("protocolVersion","simulation/1.0").put("coreVersion","simulation-core/1.0.0").put("seed",item.getSeed()).put("maxSteps",item.getMaxSteps());
        if("baseline-neutral".equals(persona))simulation.putObject("observationPolicy").put("kind","FULL");else simulation.putObject("observationPolicy").put("kind","PERSONA").put("visionRadiusPx",VISION.get(persona));
        root.putObject("policy").put("kind",kind).put("policyId","LLM".equals(kind)?"llm-step":"deterministic-heuristic").put("policyVersion","1.0").put("policyDigest","LLM".equals(kind)?LLM_DIGEST:DETERMINISTIC_DIGEST);
        root.putObject("persona").put("personaId",persona).put("personaVersion","1.0").put("personaDigest",PERSONA_DIGESTS.get(persona)).put("policySeed",item.getPolicySeed());
        if("LLM".equals(kind))root.putObject("model").put("provider","openai-compatible").put("model",playerModel).putNull("modelVersion").put("promptTemplateId","player-step").put("promptVersion","1.0");else root.putNull("model");
        root.put("metricVersion","score-delta/1.0");root.set("gameConfig",config.deepCopy());
        root.putObject("budgets").put("maxDecisions",item.getMaxSteps()).put("maxModelCalls","LLM".equals(kind)?Math.min(item.getMaxSteps(),500):0).put("maxRestarts",0).put("decisionTimeoutMs",5000).put("wallTimeoutMs",120000);
        return root;
    }
    private void validateItems(CreatePlayerRunRequest request){Set<String>keys=new java.util.HashSet<>();for(var item:request.getEpisodes()){if(!keys.add(item.getClientEpisodeKey())||!PERSONA_DIGESTS.containsKey(item.getPersonaId()))throw new BusinessException(ErrorCode.PLAYER_RUN_INVALID);if("LLM".equals(item.getPolicyKind())&&!"default".equals(item.getModelKey()))throw new BusinessException(ErrorCode.PLAYER_RUN_INVALID);if("DETERMINISTIC".equals(item.getPolicyKind())&&item.getModelKey()!=null)throw new BusinessException(ErrorCode.PLAYER_RUN_INVALID);}}
    private GameProject ownedProject(Long userId,String uuid){if(userId==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);GameProject p=projects.selectOne(new LambdaQueryWrapper<GameProject>().eq(GameProject::getProjectUuid,uuid).eq(GameProject::getUserId,userId));if(p==null)throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);return p;}
    private PlayerRunVO vo(PlayerRun r,boolean reused){return PlayerRunVO.builder().runUuid(r.getRunUuid()).prototypeVersionUuid(r.getPrototypeVersionUuid()).clientBatchKey(r.getClientBatchKey()).status(r.getStatus()).persistedBatchUuid(r.getPersistedBatchUuid()).attempt(r.getAttempt()).errorCode(r.getErrorCode()).errorMessage(r.getErrorMessage()).createdAt(r.getCreatedAt()).completedAt(r.getCompletedAt()).reused(reused).build();}
    private String safeTrace(String value){return value!=null&&value.matches("[A-Za-z0-9._:-]{8,64}")?value:UUID.randomUUID().toString();}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record PlayerRunRequested(String runUuid){}
}
