package com.example.gameworkbench.service;

import com.example.gameworkbench.entity.WorkflowRunEvent;

public interface WorkflowRunEventRecorder {
    WorkflowRunEvent record(String workflowRunUuid, String eventType, String eventKey, String stepKey,
                            String status, Integer attempt, String artifactUuid, String traceId);
}
