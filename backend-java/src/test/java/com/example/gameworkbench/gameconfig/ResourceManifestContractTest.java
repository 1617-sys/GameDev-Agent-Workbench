package com.example.gameworkbench.gameconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.evaluation.RuntimeCapabilityRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ResourceManifestContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ResourceManifestContract contract = new ResourceManifestContract(mapper,
            new RuntimeCapabilityRegistry(), new GameConfigContract(mapper));

    @Test
    void derivesOnlyConfiguredBuiltInKeysWithTraceableSource() throws Exception {
        String uuid = "11111111-1111-1111-1111-111111111111";
        String digest = "a".repeat(64);
        ResourceManifestContractResult result = contract.derive(uuid, digest, fixture());

        assertThat(result.valid()).isTrue();
        JsonNode manifest = mapper.readTree(result.canonicalContent());
        assertThat(manifest.path("sourceArtifactUuid").asText()).isEqualTo(uuid);
        assertThat(manifest.path("sourceConfigDigest").asText()).isEqualTo(digest);
        assertThat(manifest.path("runtimeCapabilityVersion").asText()).isEqualTo(RuntimeCapabilityRegistry.VERSION);
        Set<String> keys = java.util.stream.StreamSupport.stream(manifest.path("resources").spliterator(), false)
                .map(node -> node.path("key").asText()).collect(Collectors.toSet());
        assertThat(keys).containsExactlyInAnyOrder("player.blue", "obstacle.stone", "collectible.artifact",
                "collectible.gem", "enemy.guard", "exit.door", "sfx.collect", "sfx.hit", "sfx.win", "sfx.lose");
        assertThat(result.canonicalContent()).doesNotContain("http://", "https://", "data:", "../");
    }

    @Test
    void rejectsUnknownKeysCategoryMismatchAndInvalidSource() throws Exception {
        JsonNode root = mapper.readTree(contract.derive("11111111-1111-1111-1111-111111111111", "a".repeat(64), fixture())
                .canonicalContent());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("sourceArtifactUuid", "not-a-uuid");
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("resources").get(0)).put("key", "https://evil.invalid/a.png");
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("resources").get(1)).put("category", "remote");
        ResourceManifestContractResult result = contract.validate(mapper.writeValueAsString(root));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting(value -> value.code())
                .contains("FORMAT", "RESOURCE_KEY_NOT_ALLOWED", "RESOURCE_CATEGORY_MISMATCH");
    }

    private String fixture() throws Exception {
        Path path = Path.of("..", "docs", "requirements", "v3", "examples", "game-config-2.0", "valid-minimal.json");
        if (!Files.exists(path)) path = Path.of("docs", "requirements", "v3", "examples", "game-config-2.0", "valid-minimal.json");
        return Files.readString(path);
    }
}
