package com.example.gameworkbench.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;
import com.example.gameworkbench.entity.WorkflowRun;
import com.fasterxml.jackson.databind.ObjectMapper;

class GameConfigWorkflowEvaluationHookTest {
    private final GameConfigWorkflowEvaluationHook hook = new GameConfigWorkflowEvaluationHook(new ObjectMapper());
    private final WorkflowStepPlan plan = new WorkflowStepPlan("game_config_generate", 4, AgentType.GAME_CONFIG_GENERATE,
            ArtifactType.GAME_CONFIG, List.of());

    @Test
    void validatesAndCanonicalizesSupportedAliases() {
        WorkflowEvaluationResult result = evaluate("""
                {"game_config":{"version":"1.0","title":"Demo","game_type":"top_down_collect",
                "world":{"width":"960","height":540},"player":{"x":1,"y":2},"collectibles":[],"enemies":[],
                "exit":{"x":3,"y":4},"rules":{},"ui":{}}}
                """);
        assertThat(result.passed()).isTrue(); assertThat(result.schemaKey()).isEqualTo("game-config");
        assertThat(result.schemaVersion()).isEqualTo("1.0");
        assertThat(result.normalizedContent()).contains("\"gameType\":\"top_down_collect\"").contains("\"items\":[]");
    }

    @Test void rejectsInvalidJson() { rejects("{bad", "JSON object"); }
    @Test void rejectsMissingRequiredStructures() { rejects("{\"version\":\"1.0\",\"title\":\"Demo\",\"gameType\":\"top_down_collect\"}", "missing world"); }
    @Test void rejectsUnsupportedGameType() { rejects(valid("\"gameType\":\"platformer\""), "unsupported gameType"); }
    @Test void rejectsInvalidCoordinatesAndArrays() { rejects(valid("\"world\":{\"width\":\"bad\",\"height\":540},\"items\":{}"), "world.width"); }

    private WorkflowEvaluationResult evaluate(String content) {
        WorkflowRun run = new WorkflowRun(); run.setSchemaVersion("game-config/1.0");
        return hook.evaluate(new WorkflowExecutionContext(run, "project", "input", List.of(plan)), plan,
                new StepExecutionResult(new StepOutput(content, null, null, null), 1L));
    }
    private void rejects(String content, String fragment) {
        assertThatThrownBy(() -> evaluate(content)).isInstanceOf(WorkflowEvaluationException.class).hasMessageContaining(fragment);
    }
    private String valid(String replacement) {
        String base = "\"version\":\"1.0\",\"title\":\"Demo\",\"gameType\":\"top_down_collect\",\"world\":{\"width\":960,\"height\":540},\"player\":{\"x\":1,\"y\":2},\"items\":[],\"enemies\":[],\"exit\":{\"x\":3,\"y\":4},\"rules\":{},\"ui\":{}";
        if (replacement.startsWith("\"gameType")) return "{" + base.replace("\"gameType\":\"top_down_collect\"", replacement) + "}";
        return "{" + base.replace("\"world\":{\"width\":960,\"height\":540},\"player\":{\"x\":1,\"y\":2},\"items\":[]",
                replacement + ",\"player\":{\"x\":1,\"y\":2}") + "}";
    }
}
