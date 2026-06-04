package com.example.gameworkbench.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.dto.demo.GameDemoStreamRequest;

public interface DemoStreamService {

    SseEmitter streamGameDemo(Long userId, GameDemoStreamRequest request);
}
