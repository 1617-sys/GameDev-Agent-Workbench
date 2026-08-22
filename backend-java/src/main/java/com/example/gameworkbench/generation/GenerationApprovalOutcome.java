package com.example.gameworkbench.generation;

import java.time.LocalDateTime;

public record GenerationApprovalOutcome(
        String approvalUuid,
        String generationRunUuid,
        String decision,
        String reason,
        Long actorUserId,
        LocalDateTime createdAt,
        boolean reused) {
}
