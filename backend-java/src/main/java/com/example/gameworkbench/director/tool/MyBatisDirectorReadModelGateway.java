package com.example.gameworkbench.director.tool;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.MachineEpisodeMapper;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class MyBatisDirectorReadModelGateway implements DirectorReadModelGateway {
    private final PrototypeVersionMapper versions;
    private final PlayerRunMapper playerRuns;
    private final MachineEpisodeMapper episodes;
    private final AgentArtifactMapper artifacts;
    private final ObjectMapper json;

    @Override public JsonNode getPrototypeVersion(long projectId,String uuid){
        PrototypeVersion version=ownedVersion(projectId,uuid);return versionNode(version);
    }
    @Override public JsonNode getMachineEpisodeMetrics(long projectId,String uuid){
        ownedVersion(projectId,uuid);var rows=episodes.selectForAggregate(projectId,uuid);long completed=rows.stream().filter(e->"COMPLETED".equals(e.getExecutionStatus())).count();long won=rows.stream().filter(e->"SUCCESS".equals(e.getOutcome())).count();
        double meanSteps=rows.stream().filter(e->e.getStepCount()!=null).mapToInt(e->e.getStepCount()).average().orElse(0);
        return json.createObjectNode().put("prototypeVersionUuid",uuid).put("episodeCount",rows.size()).put("completedCount",completed).put("completionRate",rows.isEmpty()?0:(double)won/rows.size()).put("meanSteps",meanSteps);
    }
    @Override public JsonNode getPlayerRunStatus(long projectId,String uuid){
        PlayerRun run=playerRuns.selectByUuid(uuid);if(run==null)throw new BusinessException(ErrorCode.PLAYER_RUN_NOT_FOUND);if(run.getProjectId()!=projectId)forbidden();
        return json.createObjectNode().put("playerRunUuid",uuid).put("prototypeVersionUuid",run.getPrototypeVersionUuid()).put("status",run.getStatus()).put("attempt",run.getAttempt()==null?0:run.getAttempt()).put("persistedBatchUuid",run.getPersistedBatchUuid());
    }
    @Override public JsonNode comparePrototypeConfigs(long projectId,String baselineUuid,String candidateUuid){
        PrototypeVersion baseline=ownedVersion(projectId,baselineUuid), candidate=ownedVersion(projectId,candidateUuid);JsonNode left=config(projectId,baseline),right=config(projectId,candidate);Set<String> names=new HashSet<>();left.fieldNames().forEachRemaining(names::add);right.fieldNames().forEachRemaining(names::add);ArrayNode changed=json.createArrayNode();names.stream().sorted().filter(name->!left.path(name).equals(right.path(name))).limit(100).forEach(changed::add);
        ObjectNode result=json.createObjectNode();result.set("baseline",versionNode(baseline));result.set("candidate",versionNode(candidate));result.set("changedTopLevelFields",changed);result.put("sameConfigDigest",baseline.getConfigDigest().equals(candidate.getConfigDigest()));return result;
    }
    private PrototypeVersion ownedVersion(long projectId,String uuid){PrototypeVersion version=versions.selectByUuid(uuid);if(version==null)throw new BusinessException(ErrorCode.PROTOTYPE_VERSION_NOT_FOUND);if(version.getProjectId()!=projectId)forbidden();return version;}
    private JsonNode config(long projectId,PrototypeVersion version){AgentArtifact artifact=artifacts.selectByArtifactUuid(version.getGameConfigArtifactUuid());if(artifact==null)throw new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND);if(artifact.getProjectId()!=projectId)forbidden();try{return json.readTree(artifact.getContent());}catch(Exception e){throw new BusinessException(ErrorCode.DIRECTOR_TOOL_INVALID);}}
    private ObjectNode versionNode(PrototypeVersion value){return json.createObjectNode().put("versionUuid",value.getVersionUuid()).put("versionNumber",value.getVersionNumber()).put("parentVersionUuid",value.getParentVersionUuid()).put("configDigest",value.getConfigDigest()).put("runtimeCapabilityVersion",value.getRuntimeCapabilityVersion());}
    private void forbidden(){throw new BusinessException(ErrorCode.DIRECTOR_TOOL_FORBIDDEN);}
}
