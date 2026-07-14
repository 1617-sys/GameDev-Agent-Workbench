package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.service.WorkflowRunQueryService;
import com.example.gameworkbench.vo.workflow.WorkflowRunSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectUuid}/workflow-runs")
@RequiredArgsConstructor
public class ProjectWorkflowRunQueryController {

    private final WorkflowRunQueryService workflowRunQueryService;

    @GetMapping
    public ApiResponse<List<WorkflowRunSummaryVO>> listProjectRuns(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid
    ) {
        return ApiResponse.success(workflowRunQueryService.listProjectRuns(userId, projectUuid));
    }
}
