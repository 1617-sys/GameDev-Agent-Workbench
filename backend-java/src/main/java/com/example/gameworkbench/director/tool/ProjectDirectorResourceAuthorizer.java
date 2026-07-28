package com.example.gameworkbench.director.tool;

import java.util.Objects;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class ProjectDirectorResourceAuthorizer implements DirectorResourceAuthorizer {
    private final GameProjectMapper projects;
    private final PrototypeVersionMapper versions;
    private final PlayerRunMapper playerRuns;
    @Override public boolean mayRead(long userId,long projectId,String toolName,JsonNode arguments){
        GameProject project=projects.selectById(projectId);if(project==null||!Objects.equals(project.getUserId(),userId))return false;
        return switch(toolName){
            case "GET_PLAYER_RUN_STATUS" -> owned(playerRuns.selectByUuid(arguments.path("playerRunUuid").asText()),projectId);
            case "GET_PROTOTYPE_VERSION","GET_MACHINE_EPISODE_METRICS" -> owned(versions.selectByUuid(arguments.path("prototypeVersionUuid").asText()),projectId);
            case "COMPARE_PROTOTYPE_CONFIGS" -> owned(versions.selectByUuid(arguments.path("baselineVersionUuid").asText()),projectId)&&owned(versions.selectByUuid(arguments.path("candidateVersionUuid").asText()),projectId);
            default -> false;
        };
    }
    private boolean owned(PrototypeVersion value,long projectId){return value!=null&&Objects.equals(value.getProjectId(),projectId);}
    private boolean owned(PlayerRun value,long projectId){return value!=null&&Objects.equals(value.getProjectId(),projectId);}
}
