package com.example.gameworkbench.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.director.application.DirectorApplicationService;
import com.example.gameworkbench.director.persistence.DirectorRunView;
import com.example.gameworkbench.dto.director.SubmitDirectorRunRequest;
import com.example.gameworkbench.entity.DirectorRun;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor @RequestMapping("/api/projects/{projectUuid}/director-runs")
@org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'director-runs.manage')")
public class DirectorRunController {
    private final DirectorApplicationService service;
    @PostMapping public ApiResponse<DirectorRun> submit(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@RequestHeader("Idempotency-Key")String key,@RequestHeader(value="X-Trace-Id",required=false)String trace,@Valid @RequestBody SubmitDirectorRunRequest request){return ApiResponse.success(service.submit(userId,projectUuid,key,trace,request));}
    @GetMapping("/{runUuid}")public ApiResponse<DirectorRunView> get(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String runUuid){return ApiResponse.success(service.get(userId,projectUuid,runUuid));}
    @PostMapping("/{runUuid}/cancel")public ApiResponse<DirectorRun> cancel(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String runUuid,@RequestParam long expectedVersion){return ApiResponse.success(service.cancel(userId,projectUuid,runUuid,expectedVersion));}
}
