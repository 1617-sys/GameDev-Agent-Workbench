package com.example.gameworkbench.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;
import com.example.gameworkbench.entity.WorkflowRun;
import com.fasterxml.jackson.databind.ObjectMapper;

class GameConfigWorkflowEvaluationHookTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GameConfigWorkflowEvaluationHook hook = new GameConfigWorkflowEvaluationHook(mapper);
    private final WorkflowStepPlan plan = new WorkflowStepPlan("game_config_generate", 4, AgentType.GAME_CONFIG_GENERATE,
            ArtifactType.GAME_CONFIG, List.of());

    @Test
    void validatesCanonicalV2FromTheSharedFixture() throws Exception {
        WorkflowEvaluationResult result = evaluate(fixture("valid-minimal.json"), "game-config/2.0");
        assertThat(result.passed()).isTrue();
        assertThat(result.schemaKey()).isEqualTo("game-config");
        assertThat(result.schemaVersion()).isEqualTo("2.0");
        assertThat(mapper.readTree(result.normalizedContent()).path("metadata").path("gameType").asText())
                .isEqualTo("arcade_collect");
        assertThat(result.normalizedContent()).doesNotContain("top_down_collect", "items", "rules", "theme");
    }

    @Test
    void migratesFrozenLegacyRunsButForbidsLegacyOutputFromNewRuns() throws Exception {
        WorkflowEvaluationResult migrated = evaluate(fixture("legacy-valid-1.0.json"), "game-config/1.0");
        assertThat(migrated.schemaVersion()).isEqualTo("2.0");
        assertThat(migrated.summary()).contains("migrated");
        assertThat(mapper.readTree(migrated.normalizedContent()))
                .isEqualTo(mapper.readTree(fixture("legacy-valid-1.0.migrated.json")));
        assertThatThrownBy(() -> evaluate(fixture("legacy-valid-1.0.json"), "game-config/2.0"))
                .isInstanceOf(WorkflowEvaluationException.class).hasMessageContaining("LEGACY_WRITE_NOT_ALLOWED");
    }

    @Test void rejectsInvalidJson() { rejects("{bad", "INVALID_JSON"); }
    @Test void rejectsMissingRequiredStructures() throws Exception { rejects(fixture("invalid-missing-entities.json"), "REQUIRED at $.entities"); }
    @Test void rejectsRemoteResources() throws Exception { rejects(fixture("invalid-remote-resource.json"), "RESOURCE_KEY_NOT_ALLOWED at $.player.spriteKey"); }
    @Test void rejectsOutOfBoundsPatrols() throws Exception { rejects(fixture("invalid-out-of-bounds-patrol.json"), "WORLD_BOUNDS at $.behaviors.enemyPatrols[0].distance"); }

    private WorkflowEvaluationResult evaluate(String content, String schemaVersion) {
        WorkflowRun run = new WorkflowRun();
        run.setSchemaVersion(schemaVersion);
        return hook.evaluate(new WorkflowExecutionContext(run, "project", "input", List.of(plan)), plan,
                new StepExecutionResult(new StepOutput(content, null, null, null), 1L));
    }

    private void rejects(String content, String fragment) {
        assertThatThrownBy(() -> evaluate(content, "game-config/2.0"))
                .isInstanceOf(WorkflowEvaluationException.class).hasMessageContaining(fragment);
    }

    private String fixture(String name) throws Exception {
        Path root = Path.of("..", "docs", "requirements", "v3", "examples", "game-config-2.0", name);
        if (!Files.exists(root)) root = Path.of("docs", "requirements", "v3", "examples", "game-config-2.0", name);
        return Files.readString(root);
    }
}
