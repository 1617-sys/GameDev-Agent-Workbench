package com.example.gameworkbench.service;

import com.example.gameworkbench.vo.workflow.WorkflowRunCommandVO;

public interface WorkflowRunCommandService {
    WorkflowRunCommandVO cancel(Long userId, String workflowRunUuid);
    WorkflowRunCommandVO retry(Long userId, String workflowRunUuid);
}
