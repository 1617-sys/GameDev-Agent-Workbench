package com.example.gameworkbench.vo.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentArtifactVO {

    private Long id;
    private String artifactUuid;
    private Long projectId;
    private Long agentRunId;
    private String artifactType;
    private String title;
    private String content;
    private String contentDigest;
    private String schemaKey;
    private String schemaVersion;
    private String validationSummary;
    private Integer sourceAttempt;
    private String sourceArtifactUuid;
    private String runtimeCapabilityVersion;
    private Boolean runtimeEligible;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
