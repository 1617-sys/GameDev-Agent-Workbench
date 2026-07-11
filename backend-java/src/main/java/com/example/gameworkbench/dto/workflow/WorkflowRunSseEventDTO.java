package com.example.gameworkbench.dto.workflow;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WorkflowRunSseEventDTO(
        String eventType,
        String workflowRunUuid,
        Long sequence,
        LocalDateTime occurredAt,
        String stepKey,
        String status,
        Integer attempt,
        String artifactUuid
) {
}
