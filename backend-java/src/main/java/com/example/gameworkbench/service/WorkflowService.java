package com.example.gameworkbench.service;

import org.springframework.stereotype.Service;

import com.example.gameworkbench.dto.workflow.WorkflowRunRequest;
import com.example.gameworkbench.vo.workflow.WorkflowRunVO;


@Service
public interface WorkflowService {
    WorkflowRunVO run(Long userId, WorkflowRunRequest request);

    WorkflowRunVO getWorkflowRun(Long userId, String workflowRunUuid);

}
