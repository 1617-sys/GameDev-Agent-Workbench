package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.service.GameProjectService;
import com.example.gameworkbench.vo.project.AgentRunTypeSummaryVO;
import com.example.gameworkbench.vo.project.ProjectRunSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/dashboard")
@RestController
@Slf4j
@RequiredArgsConstructor
public class DashboardController {
    private final GameProjectService gameProjectService;

    private final AgentRunService agentRunService;

    @GetMapping("/projects/summary")
    public ApiResponse<List<ProjectRunSummaryVO>> listProjectRunSummary(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(gameProjectService.selectProjectRunSummary(userId));
    }

    @GetMapping("/projects/selectAgentType")
    public ApiResponse<List<AgentRunTypeSummaryVO>> selectAgentRunTypeSummary(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(agentRunService.selectAgentRunTypeSummary(userId));
    }
}
