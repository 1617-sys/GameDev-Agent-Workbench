package com.example.gameworkbench.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.workflow.WorkflowRunRequest;
import com.example.gameworkbench.service.WorkflowService;
import com.example.gameworkbench.vo.workflow.WorkflowRunVO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/game-design/run")
    public ApiResponse<WorkflowRunVO> run(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkflowRunRequest request
    ) {
        return ApiResponse.success(workflowService.run(userId, request));
    }

    @GetMapping("/{workflowRunUuid}")
    public ApiResponse<WorkflowRunVO> getWorkflowRun(
            @AuthenticationPrincipal Long userId,
            @PathVariable String workflowRunUuid
    ) {
        return ApiResponse.success(workflowService.getWorkflowRun(userId, workflowRunUuid));
    }
}
