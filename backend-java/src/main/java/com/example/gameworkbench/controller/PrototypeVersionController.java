package com.example.gameworkbench.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.prototype.CreatePrototypeVersionRequest;
import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.example.gameworkbench.vo.prototype.PrototypeVersionComparisonVO;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectUuid}/prototype-versions")
@RequiredArgsConstructor
public class PrototypeVersionController {
    private final PrototypeVersionService service;

    @GetMapping
    public ApiResponse<List<PrototypeVersionVO>> list(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid) {
        return ApiResponse.success(service.list(userId, projectUuid));
    }

    @GetMapping("/{versionUuid}")
    public ApiResponse<PrototypeVersionVO> get(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String versionUuid) {
        return ApiResponse.success(service.get(userId, projectUuid, versionUuid));
    }

    @GetMapping("/compare")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'prototype-versions.manage')")
    public ApiResponse<PrototypeVersionComparisonVO> compare(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @RequestParam String left, @RequestParam String right) {
        return ApiResponse.success(service.compare(userId, projectUuid, left, right));
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'prototype-versions.manage')")
    public ApiResponse<PrototypeVersionVO> create(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePrototypeVersionRequest request) {
        return ApiResponse.success(service.createFromArtifact(userId, projectUuid, idempotencyKey,
                request.getArtifactUuid()));
    }

    @PostMapping("/{parentVersionUuid}/tune")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'prototype-versions.manage')")
    public ApiResponse<PrototypeVersionVO> tune(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String parentVersionUuid,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TunePrototypeVersionRequest request) {
        return ApiResponse.success(service.tune(userId, projectUuid, parentVersionUuid, idempotencyKey, request));
    }
}
