package com.example.gameworkbench.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.evaluation.EvaluationOrchestrator;
import com.example.gameworkbench.evaluation.RuntimeCapabilityRegistry;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.ResourceManifestContract;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Test
    void persistsEligibleConfigAndDerivedManifestWithAttemptProvenance() throws Exception {
        AgentArtifactMapper mapper = Mockito.mock(AgentArtifactMapper.class);
        AtomicLong ids = new AtomicLong(10);
        when(mapper.insert(any(AgentArtifact.class))).thenAnswer(invocation -> {
            invocation.<AgentArtifact>getArgument(0).setId(ids.getAndIncrement());
            return 1;
        });
        EvaluationOrchestrator orchestrator = Mockito.mock(EvaluationOrchestrator.class);
        Mockito.doAnswer(invocation -> {
            AgentArtifact artifact = invocation.getArgument(0);
            artifact.setRuntimeEligible(true);
            artifact.setRuntimeCapabilityVersion(RuntimeCapabilityRegistry.VERSION);
            return null;
        }).when(orchestrator).evaluate(any(AgentArtifact.class));
        ObjectMapper json = new ObjectMapper();
        ResourceManifestContract manifest = new ResourceManifestContract(json, new RuntimeCapabilityRegistry(),
                new GameConfigContract(json));
        WorkflowRunEventRecorder events = Mockito.mock(WorkflowRunEventRecorder.class);

        WorkflowRun run = new WorkflowRun(); run.setProjectId(3L); run.setUserId(7L); run.setWorkflowRunUuid("run"); run.setTraceId("trace");
        WorkflowStepRun step = new WorkflowStepRun(); step.setId(8L); step.setAttempt(2);
        WorkflowStepPlan plan = new WorkflowStepPlan("game_config_generate", 4, AgentType.GAME_CONFIG_GENERATE,
                ArtifactType.GAME_CONFIG, List.of());
        String content = java.nio.file.Files.readString(java.nio.file.Path.of("..", "docs", "requirements", "v3",
                "examples", "game-config-2.0", "valid-minimal.json"));
        StepExecutionResult result = new StepExecutionResult(new StepOutput(content, null, null, null), 4L,
                new WorkflowEvaluationResult(true, "game-config", "2.0", content, "validated"));

        PrototypeVersionService prototypes = Mockito.mock(PrototypeVersionService.class);
        new DefaultArtifactWriter(mapper, events, orchestrator, manifest, prototypes).write(
                new WorkflowExecutionContext(run, "project", "brief", List.of(plan)), plan, step, result);

        ArgumentCaptor<AgentArtifact> artifacts = ArgumentCaptor.forClass(AgentArtifact.class);
        verify(mapper, Mockito.times(2)).insert(artifacts.capture());
        AgentArtifact config = artifacts.getAllValues().get(0);
        AgentArtifact resource = artifacts.getAllValues().get(1);
        assertThat(config.getArtifactType()).isEqualTo("GAME_CONFIG");
        assertThat(config.getSourceAttempt()).isEqualTo(2);
        assertThat(config.getContentDigest()).hasSize(64);
        assertThat(resource.getArtifactType()).isEqualTo("RESOURCE_MANIFEST");
        assertThat(resource.getSourceAttempt()).isEqualTo(2);
        assertThat(resource.getSourceArtifactUuid()).isEqualTo(config.getArtifactUuid());
        assertThat(resource.getContent()).contains(config.getArtifactUuid(), config.getContentDigest())
                .doesNotContain("https://", "data:");
        verify(prototypes).createFromWorkflow(7L, 3L, "run", config);
    }

    @Test
    void keepsRejectedRawConfigAsIneligibleEvidenceAndFailsTheStep() {
        AgentArtifactMapper mapper = Mockito.mock(AgentArtifactMapper.class);
        when(mapper.insert(any(AgentArtifact.class))).thenAnswer(invocation -> {
            invocation.<AgentArtifact>getArgument(0).setId(10L);
            return 1;
        });
        EvaluationOrchestrator orchestrator = Mockito.mock(EvaluationOrchestrator.class);
        WorkflowRun run = new WorkflowRun(); run.setProjectId(3L); run.setWorkflowRunUuid("run");
        WorkflowStepRun step = new WorkflowStepRun(); step.setId(8L); step.setAttempt(1);
        WorkflowStepPlan plan = new WorkflowStepPlan("game_config_generate", 4, AgentType.GAME_CONFIG_GENERATE,
                ArtifactType.GAME_CONFIG, List.of());
        String summary = "GameConfig validation failed: INVALID_JSON at $";
        StepExecutionResult result = new StepExecutionResult(new StepOutput("{bad", null, null, null), 4L,
                new WorkflowEvaluationResult(false, "game-config", "2.0", "{bad", summary));

        assertThatThrownBy(() -> new DefaultArtifactWriter(mapper, Mockito.mock(WorkflowRunEventRecorder.class),
                orchestrator, Mockito.mock(ResourceManifestContract.class)).write(
                        new WorkflowExecutionContext(run, "project", "brief", List.of(plan)), plan, step, result))
                .isInstanceOf(WorkflowEvaluationException.class).hasMessage(summary);
        ArgumentCaptor<AgentArtifact> artifact = ArgumentCaptor.forClass(AgentArtifact.class);
        verify(mapper).insert(artifact.capture());
        assertThat(artifact.getValue().getContent()).isEqualTo("{bad");
        assertThat(artifact.getValue().getRuntimeEligible()).isFalse();
        verify(orchestrator).evaluate(artifact.getValue());
    }
}
