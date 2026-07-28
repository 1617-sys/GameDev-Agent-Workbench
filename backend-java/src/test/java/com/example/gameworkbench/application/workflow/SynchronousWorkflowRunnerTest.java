package com.example.gameworkbench.application.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class SynchronousWorkflowRunnerTest {
    @Test
    void shouldRunFrozenPlansAndPersistSuccess() {
        WorkflowRunMapper runs = mock(WorkflowRunMapper.class);
        WorkflowStepRunMapper steps = mock(WorkflowStepRunMapper.class);
        WorkflowRun run = new WorkflowRun(); run.setId(1L); run.setUserId(2L); run.setWorkflowRunUuid("run"); run.setStatus("RUNNING");
        run.setInputContent("input"); run.setWorkflowDefinitionSnapshot("""
          {"steps":[{"stepKey":"a","stepOrder":1,"agentType":"GAME_CONCEPT","artifactType":"GAME_CONCEPT_RESULT","dependsOn":[]}]}
          """);
        when(runs.selectOne(any())).thenReturn(run);
        when(steps.selectByWorkflowRunUuid("run")).thenReturn(List.of());
        when(steps.updateById(any(WorkflowStepRun.class))).thenReturn(1);
        when(runs.updateById(any(WorkflowRun.class))).thenReturn(1);
        WorkflowStepExecutor executor = mock(WorkflowStepExecutor.class);
        when(executor.supports(any())).thenReturn(true);
        when(executor.execute(any(), any())).thenReturn(new StepExecutionResult(new StepOutput("out", null, null, null), 1L));

        ArtifactWriter writer = mock(ArtifactWriter.class);
        when(writer.write(any(), any(), any(), any())).thenAnswer(i -> i.<StepExecutionResult>getArgument(3).output());
        new SynchronousWorkflowRunner(runs, steps, new WorkflowStepPlanParser(new ObjectMapper()), List.of(executor), writer)
                .run("run", "project", WorkflowExecutionListener.noop());

        verify(executor).execute(any(), any());
        verify(runs, atLeastOnce()).updateById(run);
        verify(steps, atLeast(2)).updateById(any(WorkflowStepRun.class));
    }

    @Test
    void shouldShortCircuitAfterStepFailureAndRejectTerminalRun() {
        WorkflowRunMapper runs = mock(WorkflowRunMapper.class); WorkflowStepRunMapper steps = mock(WorkflowStepRunMapper.class);
        WorkflowRun run = new WorkflowRun(); run.setId(1L); run.setWorkflowRunUuid("run"); run.setStatus("RUNNING"); run.setInputContent("i");
        run.setWorkflowDefinitionSnapshot("{\"steps\":[{\"stepKey\":\"a\",\"stepOrder\":1,\"agentType\":\"GAME_CONCEPT\",\"artifactType\":\"GAME_CONCEPT_RESULT\",\"dependsOn\":[]},{\"stepKey\":\"b\",\"stepOrder\":2,\"agentType\":\"TASK_BREAKDOWN\",\"artifactType\":\"TASK_BREAKDOWN_RESULT\",\"dependsOn\":[\"a\"]}]}");
        when(runs.selectOne(any())).thenReturn(run); when(steps.selectByWorkflowRunUuid("run")).thenReturn(List.of());
        when(steps.updateById(any(WorkflowStepRun.class))).thenReturn(1); when(runs.updateById(any(WorkflowRun.class))).thenReturn(1);
        WorkflowStepExecutor executor = mock(WorkflowStepExecutor.class); when(executor.supports(any())).thenReturn(true); when(executor.execute(any(), any())).thenThrow(new IllegalStateException("agent failed"));
        SynchronousWorkflowRunner runner = new SynchronousWorkflowRunner(runs, steps, new WorkflowStepPlanParser(new ObjectMapper()), List.of(executor), mock(ArtifactWriter.class));
        assertThatThrownBy(() -> runner.run("run", "p", (type, key) -> { throw new RuntimeException("listener"); })).isInstanceOf(IllegalStateException.class);
        verify(executor, times(1)).execute(any(), any()); verify(runs).updateById(run);
        run.setStatus("SUCCESS"); assertThatThrownBy(() -> runner.run("run", "p", null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldPersistRejectedArtifactEvidenceAndFailWorkflowWhenGameConfigEvaluationFails() {
        WorkflowRunMapper runs = mock(WorkflowRunMapper.class); WorkflowStepRunMapper steps = mock(WorkflowStepRunMapper.class);
        WorkflowRun run = new WorkflowRun(); run.setId(1L); run.setUserId(2L); run.setWorkflowRunUuid("run"); run.setStatus("RUNNING"); run.setSchemaVersion("game-config/1.0"); run.setInputContent("input");
        run.setWorkflowDefinitionSnapshot("{\"steps\":[{\"stepKey\":\"game_config_generate\",\"stepOrder\":1,\"agentType\":\"GAME_CONFIG_GENERATE\",\"artifactType\":\"GAME_CONFIG\",\"dependsOn\":[]}]}");
        when(runs.selectOne(any())).thenReturn(run); when(steps.selectByWorkflowRunUuid("run")).thenReturn(List.of());
        when(steps.updateById(any(WorkflowStepRun.class))).thenReturn(1); when(runs.updateById(any(WorkflowRun.class))).thenReturn(1);
        WorkflowStepExecutor executor = mock(WorkflowStepExecutor.class); when(executor.supports(any())).thenReturn(true);
        when(executor.execute(any(), any())).thenReturn(new StepExecutionResult(new StepOutput("{bad", null, null, null), 1L));
        ArtifactWriter writer = mock(ArtifactWriter.class);
        when(writer.write(any(), any(), any(), any())).thenAnswer(invocation -> {
            StepExecutionResult rejected = invocation.getArgument(3);
            throw new WorkflowEvaluationException(rejected.evaluation().summary());
        });
        SynchronousWorkflowRunner runner = new SynchronousWorkflowRunner(runs, steps, new WorkflowStepPlanParser(new ObjectMapper()), List.of(executor), writer,
                List.of(new GameConfigWorkflowEvaluationHook(new ObjectMapper())));
        assertThatThrownBy(() -> runner.run("run", "project", null)).isInstanceOf(WorkflowEvaluationException.class);
        verify(writer).write(any(), any(), any(), argThat(result -> !result.evaluation().passed()
                && "{bad".equals(result.evaluation().normalizedContent())));
        verify(steps, atLeast(2)).updateById(argThat((WorkflowStepRun step) -> "FAILED".equals(step.getStatus()) && step.getValidationSummary().contains("GameConfig validation failed")));
        verify(runs).updateById(run);
    }
}
