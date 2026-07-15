package com.example.gameworkbench.export;

import java.time.LocalDateTime;
import java.util.Map;

public record FrozenPrototypeExport(
        String formatVersion,
        Long projectId,
        String projectUuid,
        String projectName,
        String prototypeBrief,
        String versionUuid,
        Integer versionNumber,
        LocalDateTime versionCreatedAt,
        String configArtifactUuid,
        String configDigest,
        String gameConfig,
        String resourceManifestArtifactUuid,
        String resourceManifestDigest,
        String resourceManifest,
        String runtimeCapabilityVersion,
        String playtestSnapshotAt,
        String playtestSummary,
        String playtestSummaryDigest,
        Map<String, FrozenArtifact> designArtifacts,
        FrozenArtifact balanceSuggestion) {
    public record FrozenArtifact(String artifactUuid,String contentDigest,String content) {}
}
