package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.entity.WorkflowRunEvent;
import com.example.gameworkbench.service.WorkflowRunEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringWorkflowRunEventPublisher implements WorkflowRunEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishPersisted(WorkflowRunEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
