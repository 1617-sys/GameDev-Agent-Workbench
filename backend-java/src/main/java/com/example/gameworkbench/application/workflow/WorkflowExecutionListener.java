package com.example.gameworkbench.application.workflow;

public interface WorkflowExecutionListener {
    void onEvent(String type, String stepKey);

    static WorkflowExecutionListener noop() {
        return (type, stepKey) -> { };
    }
}
