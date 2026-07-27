package com.example.gameworkbench.dto.episode;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PersistMachineEpisodeResultRequest {
    @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") private String episodeId;
    @NotBlank @Size(max = 80) private String clientEpisodeKey;
    @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") private String prototypeVersionUuid;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String configDigest;
    @NotBlank @Pattern(regexp = "simulation/1\\.0") private String simulationProtocolVersion;
    @NotBlank @Size(max = 80) private String coreVersion;
    @NotNull @Min(0) @Max(4294967295L) private Long seed;
    @NotNull @Min(1) @Max(1000000) private Integer maxSteps;
    @NotNull private JsonNode observationPolicy;
    @NotBlank @Size(max = 80) private String policyId;
    @NotBlank @Size(max = 40) private String policyVersion;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String policyDigest;
    @NotBlank @Size(max = 80) private String personaId;
    @NotBlank @Size(max = 40) private String personaVersion;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String personaDigest;
    @NotBlank @Pattern(regexp = "score-delta/1\\.0") private String metricVersion;
    @NotBlank @Pattern(regexp = "COMPLETED|FAILED|REJECTED|CANCELLED") private String executionStatus;
    @Pattern(regexp = "WON|HEALTH_DEPLETED|TIME_EXPIRED|MAX_STEPS|ERROR") private String terminationReason;
    @Pattern(regexp = "WON|LOST|TRUNCATED|ERROR") private String outcome;
    @NotNull @Min(0) private Integer stepCount;
    @NotNull @Min(0) private Integer acceptedActionCount;
    @NotNull @Min(0) private Integer invalidActionCount;
    @Pattern(regexp = "[0-9a-f]{64}") private String finalStateHash;
    private Integer finalScore;
    @Pattern(regexp = "[0-9a-f]{64}") private String trajectoryDigest;
    @Size(max = 255) private String trajectoryRef;
    @Min(0) private Long wallDurationMs;
    @NotNull @Size(max = 1000000) private List<JsonNode> steps;
}
