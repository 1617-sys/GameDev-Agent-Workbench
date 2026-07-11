package com.example.gameworkbench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.workflow-recovery")
public record WorkflowRecoveryProperties(boolean enabled, int batchSize, long pendingStaleMs,
                                         long queuedStaleMs, long runningHeartbeatStaleMs,
                                         int maxRecoveryAttempts) {
}
