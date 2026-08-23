package com.example.gameworkbench.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.player.CreatePlayerRunRequest;
import com.example.gameworkbench.entity.DirectorExperimentRun;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.ExperimentComparison;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.MachineEpisode;
import com.example.gameworkbench.entity.MachineEpisodeBatch;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.mapper.DirectorExperimentRunMapper;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.ExperimentComparisonMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.MachineEpisodeBatchMapper;
import com.example.gameworkbench.mapper.MachineEpisodeMapper;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.service.PlayerRunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class PlayerExperimentService {
    public static final String METRIC_VERSION="director-comparison/1.0";
    private final GameProjectMapper projects;private final DirectorRunMapper directorRuns;private final DirectorExperimentRunMapper experiments;
    private final PlayerRunService playerRuns;private final PlayerRunMapper playerRunRows;private final MachineEpisodeBatchMapper batches;private final MachineEpisodeMapper episodes;
    private final ExperimentComparisonMapper comparisons;private final ObjectMapper json;

    @Transactional public JsonNode run(long userId,long projectId,String runUuid,JsonNode args){
        GameProject project=owned(userId,projectId);DirectorRun run=directorRuns.selectByUuid(runUuid);if(run==null||!Objects.equals(run.getProjectId(),projectId))forbidden();
        String baseline=text(args,"baselineVersionUuid"),candidate=text(args,"candidateVersionUuid"),experimentKey=text(args,"experimentKey");if(!experimentKey.matches("[A-Za-z0-9._:-]{1,80}"))invalid();
        String fingerprint=digest(canonical(args));DirectorExperimentRun replay=experiments.selectIdempotent(run.getId(),experimentKey);if(replay!=null){if(!replay.getRequestFingerprint().equals(fingerprint))throw new BusinessException(ErrorCode.DIRECTOR_RUN_IDEMPOTENCY_CONFLICT);return experimentNode(replay);}
        List<String> personas=new ArrayList<>();args.path("personas").forEach(v->personas.add(v.asText()));List<Long> seeds=new ArrayList<>();args.path("seeds").forEach(v->seeds.add(v.asLong()));if(personas.isEmpty()||seeds.isEmpty()||personas.size()*seeds.size()>100)invalid();
        if(new java.util.HashSet<>(personas).size()!=personas.size()||new java.util.HashSet<>(seeds).size()!=seeds.size())invalid();int requestedEpisodes=personas.size()*seeds.size()*2;if(requestedEpisodes>remainingEpisodes(run))invalid();int maxSteps=args.path("maxSteps").asInt(2000);String policy=args.path("policyKind").asText("DETERMINISTIC");
        var baselineRun=playerRuns.submit(userId,project.getProjectUuid(),"experiment:"+runUuid+":"+experimentKey+":baseline",run.getTraceId(),request(baseline,experimentKey+":baseline",personas,seeds,maxSteps,policy));
        var candidateRun=playerRuns.submit(userId,project.getProjectUuid(),"experiment:"+runUuid+":"+experimentKey+":candidate",run.getTraceId(),request(candidate,experimentKey+":candidate",personas,seeds,maxSteps,policy));
        DirectorExperimentRun created=DirectorExperimentRun.builder().experimentUuid(UUID.randomUUID().toString()).directorRunId(run.getId()).projectId(projectId).baselineVersionUuid(baseline).candidateVersionUuid(candidate).baselinePlayerRunUuid(baselineRun.getRunUuid()).candidatePlayerRunUuid(candidateRun.getRunUuid()).idempotencyKey(experimentKey).requestFingerprint(fingerprint).status("PENDING").createdAt(LocalDateTime.now()).build();experiments.insert(created);return experimentNode(created);
    }
    public JsonNode status(long userId,long projectId,String runUuid,String experimentUuid){
        owned(userId,projectId);DirectorExperimentRun experiment=experiments.selectByUuid(experimentUuid);if(experiment==null||!Objects.equals(experiment.getProjectId(),projectId))forbidden();PlayerRun left=playerRunRows.selectByUuid(experiment.getBaselinePlayerRunUuid()),right=playerRunRows.selectByUuid(experiment.getCandidatePlayerRunUuid());
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)experimentNode(experiment);node.put("baselineStatus",left.getStatus()).put("candidateStatus",right.getStatus());node.set("baselineEpisodeRefs",refs(left));node.set("candidateEpisodeRefs",refs(right));return node;
    }
    @Transactional public JsonNode compare(long userId,long projectId,String runUuid,JsonNode args){
        owned(userId,projectId);DirectorRun run=directorRuns.selectByUuid(runUuid);if(run==null||!Objects.equals(run.getProjectId(),projectId))forbidden();String baseline=text(args,"baselineVersionUuid"),candidate=text(args,"candidateVersionUuid");List<MachineEpisode> left=loadRefs(projectId,baseline,args.path("baselineEpisodeUuids")),right=loadRefs(projectId,candidate,args.path("candidateEpisodeUuids"));int minimum=args.path("minimumSamples").asInt(3);
        String reason=null;if(left.size()<minimum||right.size()<minimum)reason="INSUFFICIENT_SAMPLES";else if(!matrix(left).equals(matrix(right)))reason="SAMPLE_WINDOW_MISMATCH";else if(left.stream().anyMatch(this::partial)||right.stream().anyMatch(this::partial))reason="PARTIAL_OR_FAILED_EPISODES";
        Metrics baselineMetrics=metrics(left),candidateMetrics=metrics(right);double maxExpertDelta=args.path("maxExpertMeanTimeDelta").asDouble(.08);double expertDelta=delta(expertMean(left),expertMean(right));boolean guardrail=expertDelta<=maxExpertDelta;boolean comparable=reason==null;boolean recommended=comparable&&guardrail&&candidateMetrics.completionRate()>=args.path("minimumCompletionRate").asDouble(0)&&candidateMetrics.completionRate()>baselineMetrics.completionRate();
        Map<String,Object> result=new LinkedHashMap<>();result.put("metricVersion",METRIC_VERSION);result.put("comparable",comparable);result.put("reason",reason);result.put("baseline",baselineMetrics);result.put("candidate",candidateMetrics);result.put("expertMeanTimeDelta",expertDelta);result.put("guardrailsPassed",guardrail);result.put("recommended",recommended);String resultJson=canonical(result),comparisonDigest=digest(resultJson);result.put("comparisonDigest",comparisonDigest);
        ExperimentComparison row=ExperimentComparison.builder().comparisonUuid(UUID.randomUUID().toString()).directorRunId(run.getId()).projectId(projectId).baselineVersionUuid(baseline).candidateVersionUuid(candidate).metricVersion(METRIC_VERSION).sampleWindowJson(canonical(Map.of("minimumSamples",minimum))).episodeRefsJson(canonical(Map.of("baseline",left.stream().map(MachineEpisode::getEpisodeUuid).sorted().toList(),"candidate",right.stream().map(MachineEpisode::getEpisodeUuid).sorted().toList()))).resultJson(canonical(result)).comparisonDigest(comparisonDigest).comparable(comparable).recommended(recommended).createdAt(LocalDateTime.now()).build();comparisons.insert(row);return json.valueToTree(result);
    }
    private CreatePlayerRunRequest request(String version,String batch,List<String>personas,List<Long>seeds,int maxSteps,String policy){CreatePlayerRunRequest out=new CreatePlayerRunRequest();out.setPrototypeVersionUuid(version);out.setClientBatchKey(batch.replace(':','-'));out.setConcurrency(Math.min(4,personas.size()*seeds.size()));List<CreatePlayerRunRequest.Item> items=new ArrayList<>();for(String persona:personas)for(long seed:seeds){var item=new CreatePlayerRunRequest.Item();item.setClientEpisodeKey((persona+"-"+seed).replace('_','-'));item.setPersonaId(persona);item.setPolicyKind(policy);item.setSeed(seed);item.setPolicySeed(seed);item.setMaxSteps(maxSteps);if("LLM".equals(policy))item.setModelKey("default");items.add(item);}out.setEpisodes(items);return out;}
    private JsonNode experimentNode(DirectorExperimentRun value){return json.createObjectNode().put("experimentUuid",value.getExperimentUuid()).put("status",value.getStatus()).put("baselineVersionUuid",value.getBaselineVersionUuid()).put("candidateVersionUuid",value.getCandidateVersionUuid()).put("baselinePlayerRunUuid",value.getBaselinePlayerRunUuid()).put("candidatePlayerRunUuid",value.getCandidatePlayerRunUuid());}
    private JsonNode refs(PlayerRun run){var out=json.createArrayNode();if(run==null||run.getPersistedBatchUuid()==null)return out;MachineEpisodeBatch batch=batches.selectByUuid(run.getPersistedBatchUuid());if(batch!=null)episodes.selectByBatchId(batch.getId()).forEach(e->out.add(e.getEpisodeUuid()));return out;}
    private List<MachineEpisode> loadRefs(long projectId,String version,JsonNode refs){List<MachineEpisode> values=new ArrayList<>();Set<String>seen=new java.util.HashSet<>();for(JsonNode ref:refs){if(!seen.add(ref.asText()))invalid();MachineEpisode episode=episodes.selectByUuid(ref.asText());if(episode==null||!Objects.equals(episode.getProjectId(),projectId)||!Objects.equals(episode.getPrototypeVersionUuid(),version)||!"MACHINE".equals(episode.getSampleSource()))invalid();values.add(episode);}values.sort(Comparator.comparing(MachineEpisode::getEpisodeUuid));return values;}
    private Set<String> matrix(List<MachineEpisode> values){Set<String>out=new java.util.TreeSet<>();for(MachineEpisode e:values)out.add(e.getPersonaId()+":"+e.getSeed()+":"+e.getPolicyId()+":"+e.getMetricVersion());return out;}
    private boolean partial(MachineEpisode e){return !"COMPLETED".equals(e.getExecutionStatus());}
    private Metrics metrics(List<MachineEpisode> values){if(values.isEmpty())return new Metrics(0,0,0,0,0);double success=values.stream().filter(e->"WON".equals(e.getOutcome())).count()/(double)values.size();double time=values.stream().filter(e->e.getWallDurationMs()!=null).mapToLong(MachineEpisode::getWallDurationMs).average().orElse(0);double failure=values.stream().filter(e->!"WON".equals(e.getOutcome())).count()/(double)values.size();long accepted=values.stream().map(MachineEpisode::getAcceptedActionCount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum(),invalid=values.stream().map(MachineEpisode::getInvalidActionCount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();return new Metrics(values.size(),success,time,failure,accepted+invalid==0?0:accepted/(double)(accepted+invalid));}
    private double expertMean(List<MachineEpisode> values){return values.stream().filter(e->"EXPERT".equals(e.getPersonaId())&&e.getWallDurationMs()!=null).mapToLong(MachineEpisode::getWallDurationMs).average().orElse(0);}
    private double delta(double baseline,double candidate){return baseline<=0?(candidate<=0?0:Double.POSITIVE_INFINITY):(candidate-baseline)/baseline;}
    private GameProject owned(long userId,long projectId){GameProject p=projects.selectById(projectId);if(p==null||!Objects.equals(p.getUserId(),userId))forbidden();return p;}
    private String text(JsonNode value,String field){String out=value.path(field).asText();if(out.isBlank()||out.length()>128)invalid();return out;}
    private int remainingEpisodes(DirectorRun run){try{if(run.getCheckpointJson()==null)return 100000;JsonNode checkpoint=json.readTree(run.getCheckpointJson());return Math.max(0,checkpoint.path("budget").path("maxEpisodes").asInt(100000)-checkpoint.path("usage").path("episodes").asInt(0));}catch(Exception e){throw new IllegalStateException(e);}}
    private String canonical(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void forbidden(){throw new BusinessException(ErrorCode.DIRECTOR_TOOL_FORBIDDEN);}private void invalid(){throw new BusinessException(ErrorCode.EXPERIMENT_NOT_COMPARABLE);}
    public record Metrics(int sampleCount,double completionRate,double meanCompletionTimeMs,double failureRate,double actionEfficiency){}
}
