package com.example.gameworkbench.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.gamespec.CreateGenerationRunRequest;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.generation.GenerationRunService;
import com.example.gameworkbench.generation.GenerationBuildOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v5/projects/{projectUuid}/generation-runs")
public class GenerationRunController {
    private final GenerationRunService service;

    @PostMapping
    public ApiResponse<GenerationRun> create(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateGenerationRunRequest request) {
        return ApiResponse.success(service.create(userId, projectUuid, idempotencyKey, request.spec()));
    }

    @GetMapping("/{runUuid}")
    public ApiResponse<GenerationRun> get(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String runUuid) {
        return ApiResponse.success(service.get(userId, projectUuid, runUuid));
    }

    @GetMapping("/{runUuid}/artifact")
    public ResponseEntity<byte[]> artifact(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String runUuid) {
        byte[] zip = service.artifact(userId, projectUuid, runUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=local-cocos-game-" + runUuid + ".zip")
                .contentLength(zip.length)
                .body(zip);
    }

    @PostMapping("/{runUuid}/build")
    public ApiResponse<GenerationBuildOutcome> build(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String runUuid,
            @RequestParam long expectedVersion) {
        return ApiResponse.success(service.build(userId, projectUuid, runUuid, expectedVersion));
    }
}
