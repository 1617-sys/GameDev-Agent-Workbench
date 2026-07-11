package com.example.gameworkbench.application.workflow;

public interface WorkflowEvaluationHook {
    boolean supports(WorkflowStepPlan stepPlan);

    WorkflowEvaluationResult evaluate(WorkflowExecutionContext context, WorkflowStepPlan stepPlan,
            StepExecutionResult result);
}
