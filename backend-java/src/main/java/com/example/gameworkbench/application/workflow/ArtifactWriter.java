package com.example.gameworkbench.application.workflow;

import com.example.gameworkbench.entity.WorkflowStepRun;

public interface ArtifactWriter {
    StepOutput write(WorkflowExecutionContext context, WorkflowStepPlan plan, WorkflowStepRun stepRun, StepExecutionResult result);
}
