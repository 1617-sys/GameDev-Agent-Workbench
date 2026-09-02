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
import com.example.gameworkbench.dto.gamespec.GenerationApprovalRequest;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.generation.GenerationRunService;
import com.example.gameworkbench.generation.GenerationPrototypeBridgeService;
import com.example.gameworkbench.generation.GenerationPrototypeBridgeResponse;
import com.example.gameworkbench.generation.GenerationBuildOutcome;
import com.example.gameworkbench.generation.GenerationApprovalOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * V5 可玩包生成流程的 REST 入口。
 *
 * <p>接口按状态机拆分为创建、构建、审批和发布。Controller 不自行判断状态，所有权限、
 * 幂等及并发规则集中在 {@link GenerationRunService}，保证网页和其他客户端遵守同一规则。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v5/projects/{projectUuid}/generation-runs")
public class GenerationRunController {
    private final GenerationRunService service;
    private final GenerationPrototypeBridgeService prototypeBridge;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'generation.build')")
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
        // 正式下载由服务层检查 RELEASED 门禁；Controller 只负责设置 ZIP 响应头。
        byte[] zip = service.artifact(userId, projectUuid, runUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=local-cocos-game-" + runUuid + ".zip")
                .contentLength(zip.length)
                .body(zip);
    }

    @GetMapping("/{runUuid}/preview-artifact")
    public ResponseEntity<byte[]> previewArtifact(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String runUuid) {
        // 试玩包可在待审批阶段下载，但仍需验证项目归属和产物摘要。
        byte[] zip = service.previewArtifact(userId, projectUuid, runUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=preview-cocos-game-" + runUuid + ".zip")
                .contentLength(zip.length)
                .body(zip);
    }

    @PostMapping("/{runUuid}/build")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'generation.build')")
    public ApiResponse<GenerationBuildOutcome> build(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String runUuid,
            // expectedVersion 是乐观锁版本，防止两个页面重复领取同一个构建任务。
            @RequestParam long expectedVersion) {
        return ApiResponse.success(service.build(userId, projectUuid, runUuid, expectedVersion));
    }

    @PostMapping("/{runUuid}/approval")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'generation.approve')")
    public ApiResponse<GenerationApprovalOutcome> approve(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String runUuid,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GenerationApprovalRequest request) {
        return ApiResponse.success(service.approve(userId, projectUuid, runUuid, idempotencyKey, request));
    }

    @PostMapping("/{runUuid}/release")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'generation.release')")
    public ApiResponse<GenerationRun> release(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String runUuid,
            // 发布是独立于审批的显式动作，并再次用版本号阻止旧请求覆盖新状态。
            @RequestParam long expectedVersion) {
        return ApiResponse.success(service.release(userId, projectUuid, runUuid, expectedVersion));
    }

    @PostMapping("/{runUuid}/prototype-version")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'prototype-versions.manage')")
    public ApiResponse<GenerationPrototypeBridgeResponse> createPrototypeVersion(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @PathVariable String runUuid,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(prototypeBridge.bridge(userId, projectUuid, runUuid, idempotencyKey));
    }

    @GetMapping("/{runUuid}/prototype-version-compatibility")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'prototype-versions.manage')")
    public ApiResponse<GenerationPrototypeBridgeResponse> inspectPrototypeCompatibility(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @PathVariable String runUuid) {
        return ApiResponse.success(prototypeBridge.inspect(userId, projectUuid, runUuid));
    }
}
