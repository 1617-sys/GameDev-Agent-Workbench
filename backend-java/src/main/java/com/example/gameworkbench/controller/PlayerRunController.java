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
import com.example.gameworkbench.dto.player.CreatePlayerRunRequest;
import com.example.gameworkbench.service.PlayerRunService;
import com.example.gameworkbench.vo.player.PlayerRunVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectUuid}/player-runs")
@org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'player-runs.read')")
public class PlayerRunController {
    private final PlayerRunService service;
    @PostMapping @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'player-runs.create')") public ApiResponse<PlayerRunVO> create(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-Trace-Id",required=false)String trace,@Valid @RequestBody CreatePlayerRunRequest request){return ApiResponse.success(service.submit(userId,projectUuid,key,trace,request));}
    @GetMapping("/{runUuid}") public ApiResponse<PlayerRunVO> get(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String runUuid){return ApiResponse.success(service.get(userId,projectUuid,runUuid));}
    @GetMapping public ApiResponse<List<PlayerRunVO>> list(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@RequestParam String prototypeVersionUuid){return ApiResponse.success(service.list(userId,projectUuid,prototypeVersionUuid));}
}
