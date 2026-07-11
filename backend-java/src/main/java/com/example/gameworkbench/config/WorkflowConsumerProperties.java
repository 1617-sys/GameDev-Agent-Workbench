package com.example.gameworkbench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging.consumer")
public record WorkflowConsumerProperties(long executionLockTtlSeconds) {
}
