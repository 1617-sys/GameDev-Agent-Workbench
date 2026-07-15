package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.telemetry.TelemetryBatchRequest;
import com.example.gameworkbench.vo.telemetry.*;

public interface PlaytestTelemetryService {
    PlaytestSessionVO createSession(Long userId, String projectUuid, String versionUuid);
    TelemetryBatchVO ingest(Long userId, String projectUuid, String sessionUuid, TelemetryBatchRequest request, int requestBytes);
    PlaytestSessionVO getSession(Long userId, String projectUuid, String sessionUuid);
    PlaytestMetricsVO metrics(Long userId, String projectUuid, String versionUuid);
    PlaytestMetricsComparisonVO compare(Long userId, String projectUuid, String left, String right);
    BalanceSuggestionVO suggest(Long userId, String projectUuid, String versionUuid, String idempotencyKey);
}
