package com.example.gameworkbench.service;

import com.example.gameworkbench.entity.WorkflowRunEvent;

/** In-process projection port for R4-03; it never changes workflow state. */
public interface WorkflowRunEventPublisher {
    void publishPersisted(WorkflowRunEvent event);
}
