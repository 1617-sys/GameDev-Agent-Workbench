package com.example.gameworkbench.application.workflow;

public record StepExecutionResult(StepOutput output, Long agentRunId, WorkflowEvaluationResult evaluation) {
    public StepExecutionResult(StepOutput output, Long agentRunId) {
        this(output, agentRunId, WorkflowEvaluationResult.notApplicable());
    }

    public StepExecutionResult withEvaluation(WorkflowEvaluationResult evaluation) {
        return new StepExecutionResult(output, agentRunId, evaluation);
    }
}
