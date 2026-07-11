package com.example.gameworkbench.application.workflow;

public record StepOutput(
        String content,
        String artifactUuid,
        String schemaKey,
        String schemaVersion
) {
}
