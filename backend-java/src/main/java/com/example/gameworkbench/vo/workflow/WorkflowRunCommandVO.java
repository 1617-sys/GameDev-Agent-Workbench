package com.example.gameworkbench.vo.workflow;

import lombok.Builder;

@Builder
public record WorkflowRunCommandVO(String workflowRunUuid, String status, Integer attempt, boolean reused) {
}
