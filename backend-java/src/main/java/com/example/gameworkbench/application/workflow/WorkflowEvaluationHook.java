package com.example.gameworkbench.application.workflow;

public interface WorkflowEvaluationHook {
    void evaluate(WorkflowExecutionContext context, WorkflowStepPlan stepPlan, StepExecutionResult result);
}
