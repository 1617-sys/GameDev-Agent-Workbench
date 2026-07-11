package com.example.gameworkbench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.workflow-rate-limit")
public record WorkflowRateLimitProperties(String policyVersion, int maxSubmissionsPerWindow,
                                          long windowSeconds, long retryAfterSeconds, long maxPendingRuns) {
}
