package com.example.gameworkbench.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkflowRateLimitProperties.class)
public class WorkflowRateLimitConfiguration {
}
