package com.example.gameworkbench.generation;

public record GenerationBuildOutcome(
        String generationRunUuid,
        String status,
        int cocosExitCode,
        String buildLogDigest,
        String cocosOutputDigest,
        String packageDigest
) {}
