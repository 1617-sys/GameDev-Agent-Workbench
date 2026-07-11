package com.example.gameworkbench.service;

import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;

import java.util.List;

public interface WorkflowRunQueryService {

    WorkflowRunDetailVO getRun(Long userId, String workflowRunUuid);

    List<WorkflowRunDetailVO.WorkflowStepReadVO> getSteps(Long userId, String workflowRunUuid);

    List<WorkflowRunDetailVO.ArtifactSummaryVO> getArtifacts(Long userId, String workflowRunUuid);
}
