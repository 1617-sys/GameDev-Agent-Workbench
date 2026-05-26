package com.example.gameworkbench.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;

import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRunService agentRunService;

    @PostMapping("/run")
    public ApiResponse<AgentRunVO> run(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AgentRunRequest request
    ) {
        return ApiResponse.success(agentRunService.run(userId, request));
    }

    @GetMapping("/runs")
    public ApiResponse<Page<AgentRunVO>> listRuns(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String projectUuid,
            @RequestParam(required = false) AgentType agentType,
            @RequestParam(required = false) AgentRunStatus status
    ) {
        return ApiResponse.success(agentRunService.listRuns(userId, pageNum, pageSize, projectUuid, agentType, status));
    }

    @GetMapping("/runs/{runUuid}")
    public ApiResponse<AgentRunVO> getRun(
            @AuthenticationPrincipal Long userId,
            @PathVariable String runUuid
    ) {
        // Implementation for fetching a specific agent run
        return ApiResponse.success(agentRunService.getRun(userId, runUuid));
    }
}
