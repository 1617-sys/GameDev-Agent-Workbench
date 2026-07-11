package com.example.gameworkbench.application.workflow;

public interface WorkflowRunner {
    void run(String workflowRunUuid, String projectUuid, WorkflowExecutionListener listener);
}
