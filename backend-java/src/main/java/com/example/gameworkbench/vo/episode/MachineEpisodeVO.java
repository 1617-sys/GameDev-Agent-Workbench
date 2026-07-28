package com.example.gameworkbench.vo.episode;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Value @Builder
public class MachineEpisodeVO {
    String episodeId;
    String batchId;
    String clientEpisodeKey;
    String sampleSource;
    String prototypeVersionUuid;
    String configDigest;
    Long seed;
    Integer maxSteps;
    String policyId;
    String policyVersion;
    String personaId;
    String personaVersion;
    JsonNode model;
    JsonNode usage;
    JsonNode audit;
    JsonNode timing;
    JsonNode error;
    String metricVersion;
    String executionStatus;
    String terminationReason;
    String outcome;
    Integer stepCount;
    Integer acceptedActionCount;
    Integer invalidActionCount;
    String finalStateHash;
    Integer finalScore;
    String trajectoryDigest;
    String trajectoryRef;
    Long wallDurationMs;
    LocalDateTime completedAt;
    List<JsonNode> steps;
}
