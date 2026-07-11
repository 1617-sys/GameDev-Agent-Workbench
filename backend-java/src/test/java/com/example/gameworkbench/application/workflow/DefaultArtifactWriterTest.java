package com.example.gameworkbench.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;

class DefaultArtifactWriterTest {
    @Test
    void persistsValidatedGameConfigMetadata() {
        AgentArtifactMapper mapper = Mockito.mock(AgentArtifactMapper.class); when(mapper.insert(any(AgentArtifact.class))).thenReturn(1);
        WorkflowRun run = new WorkflowRun(); run.setProjectId(3L);
        WorkflowStepPlan plan = new WorkflowStepPlan("game_config_generate", 1, AgentType.GAME_CONFIG_GENERATE, ArtifactType.GAME_CONFIG, List.of());
        StepExecutionResult result = new StepExecutionResult(new StepOutput("raw", null, null, null), 4L,
                new WorkflowEvaluationResult(true, "game-config", "1.0", "{\"version\":\"1.0\"}", "GameConfig contract validated"));
        StepOutput output = new DefaultArtifactWriter(mapper).write(new WorkflowExecutionContext(run, "project", "input", List.of(plan)),
                plan, new WorkflowStepRun(), result);
        ArgumentCaptor<AgentArtifact> artifact = ArgumentCaptor.forClass(AgentArtifact.class); verify(mapper).insert((AgentArtifact) artifact.capture());
        assertThat(artifact.getValue().getSchemaKey()).isEqualTo("game-config");
        assertThat(artifact.getValue().getSchemaVersion()).isEqualTo("1.0");
        assertThat(artifact.getValue().getValidationSummary()).isEqualTo("GameConfig contract validated");
        assertThat(output.content()).isEqualTo("{\"version\":\"1.0\"}");
    }
}
