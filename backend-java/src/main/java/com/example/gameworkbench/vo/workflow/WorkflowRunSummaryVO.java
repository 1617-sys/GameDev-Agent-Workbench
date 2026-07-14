package com.example.gameworkbench.vo.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Client-safe project history item without prompts or artifact bodies. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunSummaryVO {

    private String workflowRunUuid;
    private String workflowType;
    private String status;
    private Integer attempt;
    private Long timeTakenMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
