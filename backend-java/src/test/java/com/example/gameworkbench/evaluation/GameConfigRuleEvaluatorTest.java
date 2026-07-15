package com.example.gameworkbench.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class GameConfigRuleEvaluatorTest {
    private final GameConfigRuleEvaluator evaluator = new GameConfigRuleEvaluator(new ObjectMapper(), new RuntimeCapabilityRegistry());

    @Test
    void rejectsUnsupportedTemplateRemoteResourceAndWorldBounds() throws Exception {
        var unsupported = new ObjectMapper().readTree(fixture("valid-minimal.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) unsupported.path("metadata")).put("gameType", "platformer");
        assertThat(evaluator.evaluate(unsupported.toString()).violations()).extracting(RuleViolation::code).contains("CONST");
        assertThat(evaluator.evaluate(fixture("invalid-remote-resource.json")).violations()).extracting(RuleViolation::code).contains("RESOURCE_KEY_NOT_ALLOWED");
        assertThat(evaluator.evaluate(fixture("invalid-out-of-bounds-patrol.json")).violations()).extracting(RuleViolation::code).contains("WORLD_BOUNDS");
    }

    @Test
    void acceptsCanonicalAndDeterministicallyMigratedFixtures() throws Exception {
        assertThat(evaluator.evaluate(fixture("valid-minimal.json")).status()).isEqualTo("PASSED");
        assertThat(evaluator.evaluate(fixture("legacy-valid-1.0.json")).status()).isEqualTo("PASSED");
        assertThat(new RuntimeCapabilityRegistry().version()).isEqualTo("arcade-collect-runtime/1");
    }

    @Test
    void rejectsEntityOverlapWithObstacle() throws Exception {
        var config = (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(fixture("valid-minimal.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) config.path("world").path("spawn")).put("x", 360).put("y", 180);
        assertThat(evaluator.evaluate(config.toString()).violations()).extracting(RuleViolation::code).contains("WORLD_OVERLAP");
    }

    private String fixture(String name) throws Exception {
        Path path = Path.of("..", "docs", "requirements", "v3", "examples", "game-config-2.0", name);
        if (!Files.exists(path)) path = Path.of("docs", "requirements", "v3", "examples", "game-config-2.0", name);
        return Files.readString(path);
    }
}
