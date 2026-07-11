package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.service.WorkflowRunQueryService;
import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflow-runs")
@RequiredArgsConstructor
public class WorkflowRunQueryController {

    private final WorkflowRunQueryService workflowRunQueryService;

    @GetMapping("/{workflowRunUuid}")
    public ApiResponse<WorkflowRunDetailVO> getRun(
            @AuthenticationPrincipal Long userId,
            @PathVariable String workflowRunUuid
    ) {
        return ApiResponse.success(workflowRunQueryService.getRun(userId, workflowRunUuid));
    }

    @GetMapping("/{workflowRunUuid}/steps")
    public ApiResponse<List<WorkflowRunDetailVO.WorkflowStepReadVO>> getSteps(
            @AuthenticationPrincipal Long userId,
            @PathVariable String workflowRunUuid
    ) {
        return ApiResponse.success(workflowRunQueryService.getSteps(userId, workflowRunUuid));
    }

    @GetMapping("/{workflowRunUuid}/artifacts")
    public ApiResponse<List<WorkflowRunDetailVO.ArtifactSummaryVO>> getArtifacts(
            @AuthenticationPrincipal Long userId,
            @PathVariable String workflowRunUuid
    ) {
        return ApiResponse.success(workflowRunQueryService.getArtifacts(userId, workflowRunUuid));
    }
}
