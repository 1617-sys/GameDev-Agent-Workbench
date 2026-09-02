package com.example.gameworkbench.generation;

import java.util.List;

public record GenerationPrototypeBridgeResponse(
        boolean compatible,
        String prototypeVersionUuid,
        boolean reused,
        SourceSummary source,
        List<Incompatibility> reasons
) {
    public record SourceSummary(String runUuid, String sourceDigest, String runtimeIrDigest, String status) {}
    public record Incompatibility(String code, String path, String message, String expected, String actual) {}
}
