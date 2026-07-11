package com.example.gameworkbench.application.workflow;

/** Immutable validation decision that may be persisted with a structured artifact. */
public record WorkflowEvaluationResult(boolean passed, String schemaKey, String schemaVersion,
        String normalizedContent, String summary) {
    public static WorkflowEvaluationResult notApplicable() {
        return new WorkflowEvaluationResult(true, null, null, null, "not applicable");
    }
}
