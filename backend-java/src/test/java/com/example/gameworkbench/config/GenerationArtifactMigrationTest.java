package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GenerationArtifactMigrationTest {
    @Test
    void migrationAddsTraceableAttemptArtifactsAndFrozenWorkflowVersion() throws Exception {
        var migration = new ClassPathResource("db/migration/V29__close_game_generation_artifact_loop.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("content_digest", "source_attempt", "source_artifact_uuid",
                "runtime_capability_version", "uk_agent_artifact_step_type_attempt",
                "'GAME_GENERATE', 2", "insert into prompt_version");
    }
}
