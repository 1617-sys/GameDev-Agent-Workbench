package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.workflow.AsyncWorkflowSubmitRequest;
import com.example.gameworkbench.service.AsyncWorkflowSubmissionService;
import com.example.gameworkbench.vo.workflow.WorkflowSubmitVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectUuid}/workflow-runs")
@RequiredArgsConstructor
public class AsyncWorkflowController {

    private final AsyncWorkflowSubmissionService asyncWorkflowSubmissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkflowSubmitVO>> submit(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AsyncWorkflowSubmitRequest request
    ) {
        WorkflowSubmitVO response = asyncWorkflowSubmissionService.submit(userId, projectUuid, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }
}
