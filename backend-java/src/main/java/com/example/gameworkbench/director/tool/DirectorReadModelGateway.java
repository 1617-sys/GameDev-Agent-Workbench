package com.example.gameworkbench.director.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface DirectorReadModelGateway {
    JsonNode getPrototypeVersion(long projectId,String versionUuid);
    JsonNode getMachineEpisodeMetrics(long projectId,String versionUuid);
    JsonNode getPlayerRunStatus(long projectId,String playerRunUuid);
    JsonNode comparePrototypeConfigs(long projectId,String baselineVersionUuid,String candidateVersionUuid);
}
