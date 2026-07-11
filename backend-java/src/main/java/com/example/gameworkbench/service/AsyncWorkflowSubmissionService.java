package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.workflow.AsyncWorkflowSubmitRequest;
import com.example.gameworkbench.vo.workflow.WorkflowSubmitVO;

public interface AsyncWorkflowSubmissionService {
    WorkflowSubmitVO submit(Long userId, String projectUuid, String idempotencyKey, AsyncWorkflowSubmitRequest request);
}
