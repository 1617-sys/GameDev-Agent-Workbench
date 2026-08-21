package com.example.gameworkbench.artifact;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record PlayableArtifact(
        String sourceDigest,
        String payloadDigest,
        String packageDigest,
        ObjectNode manifest,
        @JsonIgnore byte[] zipBytes
) {
    public PlayableArtifact {
        zipBytes = zipBytes.clone();
    }

    @Override public byte[] zipBytes() { return zipBytes.clone(); }
}
