package com.example.gameworkbench.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaEvaluatorTest {
    private final SchemaEvaluator evaluator = new SchemaEvaluator(new ObjectMapper());

    @Test void acceptsSharedV2Fixture() throws Exception { assertThat(evaluator.evaluate(fixture("valid-minimal.json"), "game-config", "2.0").status()).isEqualTo("PASSED"); }
    @Test void acceptsMigratableHistoricalArtifactForReadEvaluation() throws Exception { assertThat(evaluator.evaluate(fixture("legacy-valid-1.0.json"), "game-config", "1.0").status()).isEqualTo("PASSED"); }
    @Test void rejectsMissingStructureBeforeNormalization() { var result = evaluator.evaluate("{}", "game-config", "2.0"); assertThat(result.status()).isEqualTo("FAILED"); assertThat(result.violations()).anyMatch(value -> value.startsWith("UNSUPPORTED_SCHEMA_VERSION@")); }
    @Test void skipsUnknownSchemaInsteadOfPretendingPass() { assertThat(evaluator.evaluate("{}", "text", "1.0").status()).isEqualTo("SKIPPED"); }

    private String fixture(String name) throws Exception {
        Path path = Path.of("..", "docs", "requirements", "v3", "examples", "game-config-2.0", name);
        if (!Files.exists(path)) path = Path.of("docs", "requirements", "v3", "examples", "game-config-2.0", name);
        return Files.readString(path);
    }
}
