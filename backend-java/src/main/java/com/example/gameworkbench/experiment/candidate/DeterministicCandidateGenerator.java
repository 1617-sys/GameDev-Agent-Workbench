package com.example.gameworkbench.experiment.candidate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.ExperimentCandidate;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.ExperimentCandidateMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.prototype.PrototypeDraftService;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class DeterministicCandidateGenerator {
    public static final String VERSION="neighbor/1.0";
    private static final Map<String,Range> RANGES=Map.of("timeLimitSeconds",new Range(30,600),"playerSpeed",new Range(80,400),"playerMaxHealth",new Range(1,5),"targetCollectibles",new Range(1,20),"enemyCount",new Range(0,12));
    private final GameProjectMapper projects;private final DirectorRunMapper runs;private final ExperimentCandidateMapper candidates;
    private final PrototypeVersionService prototypes;private final PrototypeDraftService drafts;private final ObjectMapper json;
    @Transactional public CandidatePlan generate(long userId,long projectId,String runUuid,CandidateGenerationCommand command){
        GameProject project=projects.selectById(projectId);DirectorRun run=runs.selectByUuid(runUuid);if(project==null||!Objects.equals(project.getUserId(),userId)||run==null||!Objects.equals(run.getProjectId(),projectId))throw new BusinessException(ErrorCode.DIRECTOR_TOOL_FORBIDDEN);
        if(command==null||command.maxCandidates()<1||command.maxCandidates()>100||command.goalDigest()==null||!command.goalDigest().matches("[0-9a-f]{64}")||command.directions()==null||command.directions().isEmpty()||!RANGES.keySet().containsAll(command.directions().keySet()))invalid();
        int limit=Math.min(command.maxCandidates(),remainingBudget(run));
        PrototypeVersionVO parent=prototypes.get(userId,project.getProjectUuid(),command.parentVersionUuid());Map<String,Object> fingerprintInput=new LinkedHashMap<>();fingerprintInput.put("version",VERSION);fingerprintInput.put("parent",command.parentVersionUuid());fingerprintInput.put("goal",command.goalDigest());fingerprintInput.put("directions",command.directions());fingerprintInput.put("steps",command.stepSizes()==null?Map.of():command.stepSizes());fingerprintInput.put("limit",command.maxCandidates());String inputDigest=digest(canonical(fingerprintInput));
        List<ExperimentCandidate> replay=candidates.selectPlan(run.getId(),inputDigest);if(replay!=null&&!replay.isEmpty()){List<CandidatePlan.Item> items=replay.stream().map(value->new CandidatePlan.Item(value.getOrdinalNumber(),value.getCandidateUuid(),value.getPrototypeVersionUuid(),value.getConfigDigest(),read(value.getTuningJson()))).toList();return new CandidatePlan(VERSION,inputDigest,digest(canonical(items)),items,"GENERATED");}
        List<CandidatePlan.Item> result=new ArrayList<>();Set<String> seen=new java.util.HashSet<>();Integer maxOrdinal=candidates.selectMaxOrdinal(run.getId());int ordinal=(maxOrdinal==null?0:maxOrdinal)+1;
        for(String field:command.directions().keySet().stream().sorted().toList()){
            String direction=command.directions().get(field);if(!Set.of("INCREASE","DECREASE").contains(direction))invalid();int baseline=((Number)parent.getParameters().get(field)).intValue();int step=command.stepSizes()==null?1:command.stepSizes().getOrDefault(field,1);if(step<1)invalid();
            for(int multiplier=1;multiplier<=limit&&result.size()<limit;multiplier++){
                int value=baseline+("INCREASE".equals(direction)?1:-1)*step*multiplier;Range range=RANGES.get(field);if(value<range.min||value>range.max)break;String signature=field+"="+value;if(!seen.add(signature))continue;
                TunePrototypeVersionRequest tuning=tuning(field,value);String key="candidate:"+runUuid+":"+inputDigest.substring(0,12)+":"+ordinal;PrototypeVersionVO draft=drafts.create(userId,projectId,runUuid,command.parentVersionUuid(),key,tuning);
                String candidateUuid=UUID.randomUUID().toString();Map<String,Integer> tuningMap=Map.of(field,value);LocalDateTime now=LocalDateTime.now();candidates.insert(ExperimentCandidate.builder().candidateUuid(candidateUuid).directorRunId(run.getId()).projectId(projectId).ordinalNumber(ordinal).status("DRAFT").generatorVersion(VERSION).inputDigest(inputDigest).tuningJson(canonical(tuningMap)).prototypeVersionUuid(draft.getVersionUuid()).configDigest(draft.getConfigDigest()).evidenceJson(canonical(Map.of("goalDigest",command.goalDigest(),"parentVersionUuid",command.parentVersionUuid(),"rule",direction,"step",step))).createdAt(now).updatedAt(now).build());
                result.add(new CandidatePlan.Item(ordinal,candidateUuid,draft.getVersionUuid(),draft.getConfigDigest(),tuningMap));ordinal++;
            }
        }
        String planDigest=digest(canonical(result));return new CandidatePlan(VERSION,inputDigest,planDigest,List.copyOf(result),result.isEmpty()?"NO_VALID_CANDIDATES":"GENERATED");
    }
    private TunePrototypeVersionRequest tuning(String field,int value){TunePrototypeVersionRequest out=new TunePrototypeVersionRequest();switch(field){case "timeLimitSeconds"->out.setTimeLimitSeconds(value);case "playerSpeed"->out.setPlayerSpeed(value);case "playerMaxHealth"->out.setPlayerMaxHealth(value);case "targetCollectibles"->out.setTargetCollectibles(value);case "enemyCount"->out.setEnemyCount(value);default->invalid();}return out;}
    private String canonical(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Object read(String value){try{return json.readValue(value,Object.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private int remainingBudget(DirectorRun run){try{if(run.getCheckpointJson()==null)return 100;var checkpoint=json.readTree(run.getCheckpointJson());int maximum=checkpoint.path("budget").path("maxCandidates").asInt(100);int existing=candidates.selectRunCandidates(run.getId(),run.getProjectId()).size();return Math.max(0,maximum-existing);}catch(Exception e){throw new IllegalStateException(e);}}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void invalid(){throw new BusinessException(ErrorCode.CANDIDATE_GENERATION_INVALID);}
    private record Range(int min,int max){}
}
