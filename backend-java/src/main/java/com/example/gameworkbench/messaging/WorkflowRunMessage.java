package com.example.gameworkbench.messaging;

import java.time.LocalDateTime;

/** Stable payload emitted from a committed Outbox event; consumers are introduced in R3-04. */
public record WorkflowRunMessage(
        int schemaVersion,
        String messageId,
        String eventId,
        String workflowRunUuid,
        int attempt,
        String traceId,
        LocalDateTime createdAt
) {
}
