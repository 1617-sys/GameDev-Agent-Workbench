package com.example.gameworkbench.evaluation;

import java.time.LocalDateTime;
import java.util.List;

public record RagComparisonReport(
        String status,
        String experimentKey,
        Long projectId,
        Long promptVersionId,
        String provider,
        String modelName,
        LocalDateTime from,
        LocalDateTime to,
        String retrievalVersion,
        String chunkingVersion,
        String embeddingModel,
        List<String> documentSnapshots,
        List<String> evaluationVersions,
        RagCohortStats ragOff,
        RagCohortStats ragOn,
        RagCohortStats mockRagOff,
        RagCohortStats mockRagOn
) {
}
