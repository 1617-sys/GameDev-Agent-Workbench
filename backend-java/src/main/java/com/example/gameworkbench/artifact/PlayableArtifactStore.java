package com.example.gameworkbench.artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlayableArtifactStore {
    private final Path root;

    public PlayableArtifactStore(@Value("${app.cocos.artifact-root:${java.io.tmpdir}/gamedev-playable-artifacts}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public Path put(String runUuid, PlayableArtifact artifact) {
        if (runUuid == null || !runUuid.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid run UUID");
        if (artifact == null || artifact.packageDigest() == null || !artifact.packageDigest().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid artifact digest");
        }
        try {
            Files.createDirectories(root);
            // Digest-addressed files prevent a stale, expired build worker from overwriting the
            // artifact selected by the winning database compare-and-set.
            Path target = target(runUuid, artifact.packageDigest());
            if (!target.startsWith(root)) throw new IllegalArgumentException("Artifact path escapes storage root");
            Path temporary = Files.createTempFile(root, runUuid + "-", ".tmp");
            Files.write(temporary, artifact.zipBytes());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist playable artifact", exception);
        }
    }

    public byte[] get(String runUuid, String expectedDigest) {
        if (runUuid == null || !runUuid.matches("[0-9a-f-]{36}")
                || expectedDigest == null || !expectedDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid artifact identity");
        }
        try {
            Path target = target(runUuid, expectedDigest);
            if (!target.startsWith(root) || !Files.isRegularFile(target)) throw new IllegalStateException("Artifact is unavailable");
            byte[] value = Files.readAllBytes(target);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
            if (!actual.equals(expectedDigest)) throw new IllegalStateException("Artifact digest mismatch");
            return value;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read playable artifact", exception);
        }
    }

    private Path target(String runUuid, String digest) {
        Path target = root.resolve(runUuid + "-" + digest + ".zip").normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Artifact path escapes storage root");
        return target;
    }
}
