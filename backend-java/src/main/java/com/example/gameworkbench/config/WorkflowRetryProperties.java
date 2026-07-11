package com.example.gameworkbench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging.retry")
public record WorkflowRetryProperties(int maxAttempts, long firstDelayMs, long secondDelayMs, long thirdDelayMs) {
    public long delayFor(int retryCount) {
        return retryCount <= 1 ? firstDelayMs : retryCount == 2 ? secondDelayMs : thirdDelayMs;
    }
}
