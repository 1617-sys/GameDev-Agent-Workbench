package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.service.WorkflowRunSseEmitterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class DefaultWorkflowRunSseEmitterFactory implements WorkflowRunSseEmitterFactory {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    @Override
    public SseEmitter create() {
        return new SseEmitter(TIMEOUT_MS);
    }
}
