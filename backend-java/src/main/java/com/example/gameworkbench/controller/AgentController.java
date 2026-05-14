package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;


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
    public ApiResponse<List<AgentRunVO>> listRuns(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(agentRunService.listRuns(userId));
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
