package com.example.gameworkbench.cocos;

import java.nio.file.Path;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record CocosBuildResult(
        Status status,
        int exitCode,
        String logDigest,
        String outputDigest,
        @JsonIgnore Path outputDirectory
) {
    public enum Status { SUCCEEDED, FAILED }
}
