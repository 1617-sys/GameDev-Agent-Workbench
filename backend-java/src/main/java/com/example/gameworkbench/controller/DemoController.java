package com.example.gameworkbench.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.dto.demo.GameDemoStreamRequest;
import com.example.gameworkbench.service.DemoStreamService;

import lombok.RequiredArgsConstructor;

@RestController
@Profile("!prod")
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoStreamService demoStreamService;

    @PostMapping(value = "/game/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGameDemo(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GameDemoStreamRequest request
    ) {
        return demoStreamService.streamGameDemo(userId, request);
    }
}
