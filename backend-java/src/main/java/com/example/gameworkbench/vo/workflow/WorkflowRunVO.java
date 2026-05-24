package com.example.gameworkbench.vo.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunVO {

    private Long id;
    private String workflowRunUuid;
    private Long projectId;
    private String projectUuid;
    private Long userId;
    private String workflowType;
    private String status;
    private String inputContent;
    private String summary;
    private String errorMessage;
    private Long timeTakenMs;
    private List<WorkflowStepVO> steps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStepVO {
        private Integer stepOrder;
        private String agentType;
        private String artifactType;
        private String title;
        private String content;
        private String agentRunUuid;
        private String artifactUuid;
    }
}
