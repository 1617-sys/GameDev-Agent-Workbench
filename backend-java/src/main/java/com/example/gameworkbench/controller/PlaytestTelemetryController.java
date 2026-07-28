package com.example.gameworkbench.controller;

import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.telemetry.*;
import com.example.gameworkbench.service.PlaytestTelemetryService;
import com.example.gameworkbench.vo.telemetry.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectUuid}")
public class PlaytestTelemetryController {
    private final PlaytestTelemetryService service;
    private final ObjectMapper json;

    @PostMapping("/prototype-versions/{versionUuid}/playtest-sessions")
    public ApiResponse<PlaytestSessionVO> create(@AuthenticationPrincipal Long userId, @PathVariable String projectUuid,
            @PathVariable String versionUuid, @Valid @RequestBody(required=false) CreatePlaytestSessionRequest ignored) {
        return ApiResponse.success(service.createSession(userId, projectUuid, versionUuid));
    }
    @PostMapping("/playtest-sessions/{sessionUuid}/events")
    public ApiResponse<TelemetryBatchVO> ingest(@AuthenticationPrincipal Long userId, @PathVariable String projectUuid,
            @PathVariable String sessionUuid, @Valid @RequestBody TelemetryBatchRequest request) throws Exception {
        int bytes=json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8).length;
        return ApiResponse.success(service.ingest(userId, projectUuid, sessionUuid, request, bytes));
    }
    @GetMapping("/playtest-sessions/{sessionUuid}")
    public ApiResponse<PlaytestSessionVO> get(@AuthenticationPrincipal Long userId, @PathVariable String projectUuid,
            @PathVariable String sessionUuid) { return ApiResponse.success(service.getSession(userId, projectUuid, sessionUuid)); }
    @GetMapping("/prototype-versions/{versionUuid}/playtest-metrics")
    public ApiResponse<PlaytestMetricsVO> metrics(@AuthenticationPrincipal Long userId, @PathVariable String projectUuid,
            @PathVariable String versionUuid) { return ApiResponse.success(service.metrics(userId, projectUuid, versionUuid)); }
    @GetMapping("/playtest-metrics/compare")
    public ApiResponse<PlaytestMetricsComparisonVO> compare(@AuthenticationPrincipal Long userId, @PathVariable String projectUuid,
            @RequestParam String left, @RequestParam String right) { return ApiResponse.success(service.compare(userId, projectUuid, left, right)); }
    @PostMapping("/prototype-versions/{versionUuid}/balance-suggestions")
    public ApiResponse<BalanceSuggestionVO> suggest(@AuthenticationPrincipal Long userId, @PathVariable String projectUuid,
            @PathVariable String versionUuid, @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.suggest(userId, projectUuid, versionUuid, key));
    }
}
