package com.example.gameworkbench.application.workflow;

public interface WorkflowStepExecutor {
    boolean supports(WorkflowStepPlan stepPlan);
    StepExecutionResult execute(WorkflowExecutionContext context, WorkflowStepPlan stepPlan);
}
