package com.example.gameworkbench.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface WorkflowRunSseEmitterFactory {
    SseEmitter create();
}
