package com.example.gameworkbench.dto.retrieval;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.gameworkbench.evaluation.RagCohortStats;

public record RagEvidenceResponse(
        String stepKey,
        String agentRunUuid,
        Boolean ragEnabled,
        String ragStatus,
        boolean mock,
        Integer contextBudget,
        String retrievalVersion,
        String chunkingVersion,
        String embeddingModel,
        List<ReferenceSummary> references,
        ComparisonSummary comparison
) {
    public record ReferenceSummary(
            String documentUuid,
            Integer documentVersion,
            String chunkUuid,
            Integer rank,
            BigDecimal score
    ) {
    }

    public record ComparisonSummary(
            String status,
            Long promptVersionId,
            String provider,
            String modelName,
            LocalDateTime from,
            LocalDateTime to,
            String retrievalVersion,
            String chunkingVersion,
            String embeddingModel,
            List<String> evaluationVersions,
            RagCohortStats ragOff,
            RagCohortStats ragOn
    ) {
    }
}
