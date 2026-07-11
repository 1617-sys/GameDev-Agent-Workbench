package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.service.WorkflowRunCommandService;
import com.example.gameworkbench.vo.workflow.WorkflowRunCommandVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-runs")
@RequiredArgsConstructor
public class WorkflowRunCommandController {
    private final WorkflowRunCommandService workflowRunCommandService;
    @PostMapping("/{workflowRunUuid}/cancel")
    public ResponseEntity<ApiResponse<WorkflowRunCommandVO>> cancel(@AuthenticationPrincipal Long userId, @PathVariable String workflowRunUuid) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(workflowRunCommandService.cancel(userId, workflowRunUuid)));
    }
    @PostMapping("/{workflowRunUuid}/retry")
    public ResponseEntity<ApiResponse<WorkflowRunCommandVO>> retry(@AuthenticationPrincipal Long userId, @PathVariable String workflowRunUuid) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(workflowRunCommandService.retry(userId, workflowRunUuid)));
    }
}
