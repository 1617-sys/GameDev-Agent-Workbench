package com.example.gameworkbench.vo.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only, client-safe projection of a persisted workflow run. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunDetailVO {

    private String workflowRunUuid;
    private String status;
    private Integer attempt;
    private Long definitionVersionId;
    private String schemaVersion;
    private Long statusVersion;
    private Long timeTakenMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime failedAt;
    private ErrorSummaryVO error;
    private List<String> allowedActions;
    private List<WorkflowStepReadVO> steps;
    private List<ArtifactSummaryVO> artifacts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorSummaryVO {
        private String code;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStepReadVO {
        private String stepKey;
        private Integer stepOrder;
        private String agentType;
        private String artifactType;
        private String status;
        private Integer attempt;
        private String schemaKey;
        private String schemaVersion;
        private Long timeTakenMs;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private ErrorSummaryVO error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtifactSummaryVO {
        private String artifactUuid;
        private String stepKey;
        private String type;
        private String displayName;
        private String status;
        private String url;
        private LocalDateTime createdAt;
    }
}
