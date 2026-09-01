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

/**
 * 异步工作流的 HTTP 入口。
 *
 * <p>Controller 只负责协议层工作：读取登录用户、路径参数、幂等键并触发参数校验。
 * 项目归属、限流、工作流快照和事务写入都由服务层处理，避免把业务规则散落在接口层。</p>
 */
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
        // 这里只表示请求已被可靠接收，不表示工作流已经执行完成，所以返回 202 而不是 200。
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }
}
