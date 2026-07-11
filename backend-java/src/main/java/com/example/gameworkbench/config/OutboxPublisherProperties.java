package com.example.gameworkbench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox.publisher")
public record OutboxPublisherProperties(
        int batchSize,
        long pollDelayMs,
        long claimLeaseMs,
        long retryDelayMs
) {
}
