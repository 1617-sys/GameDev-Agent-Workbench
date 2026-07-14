package com.example.gameworkbench.service;

import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;
import com.example.gameworkbench.vo.workflow.WorkflowRunSummaryVO;

import java.util.List;

public interface WorkflowRunQueryService {

    List<WorkflowRunSummaryVO> listProjectRuns(Long userId, String projectUuid);

    WorkflowRunDetailVO getRun(Long userId, String workflowRunUuid);

    List<WorkflowRunDetailVO.WorkflowStepReadVO> getSteps(Long userId, String workflowRunUuid);

    List<WorkflowRunDetailVO.ArtifactSummaryVO> getArtifacts(Long userId, String workflowRunUuid);
}
