package com.example.gameworkbench.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.prototype.PrototypeApprovalRequest;
import com.example.gameworkbench.prototype.PrototypeApprovalService;
import com.example.gameworkbench.vo.prototype.PrototypeApprovalVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor @RequestMapping("/api/projects/{projectUuid}/prototype-versions/{versionUuid}/approval")
@org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'prototype-versions.manage')")
public class PrototypeApprovalController {
    private final PrototypeApprovalService service;
    @PostMapping public ApiResponse<PrototypeApprovalVO> decide(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String versionUuid,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody PrototypeApprovalRequest request){return ApiResponse.success(service.decide(userId,projectUuid,versionUuid,key,request));}
}
