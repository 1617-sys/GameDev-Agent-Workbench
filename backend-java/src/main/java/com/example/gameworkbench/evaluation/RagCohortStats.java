package com.example.gameworkbench.evaluation;

import java.math.BigDecimal;

public record RagCohortStats(
        int samples,
        int evaluated,
        double schemaPassRate,
        double rulePassRate,
        double runtimePassRate,
        long p50LatencyMs,
        long p95LatencyMs,
        long inputTokens,
        long outputTokens,
        BigDecimal estimatedCost,
        int missingMetricSamples,
        int missingCostSamples,
        int retrievalCovered,
        int emptyRetrieval,
        int failedRetrieval,
        int mockExcluded
) {
}
