package com.example.gameworkbench.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public record RabbitMqInfrastructureProperties(
        String workflowExchange,
        String workflowQueue,
        String workflowRoutingKey
) {
}
