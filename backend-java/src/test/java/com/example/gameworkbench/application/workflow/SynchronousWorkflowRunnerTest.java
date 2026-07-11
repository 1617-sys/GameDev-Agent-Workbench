package com.example.gameworkbench.application.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        WorkflowStepExecutor executor = mock(WorkflowStepExecutor.class);
        when(executor.supports(any())).thenReturn(true);
        when(executor.execute(any(), any())).thenReturn(new StepExecutionResult(new StepOutput("out", null, null, null), 1L));

        new SynchronousWorkflowRunner(runs, steps, new WorkflowStepPlanParser(new ObjectMapper()), List.of(executor))
                .run("run", "project", WorkflowExecutionListener.noop());

        verify(executor).execute(any(), any());
        verify(runs, atLeastOnce()).updateById(run);
        verify(steps, atLeast(2)).updateById(any(WorkflowStepRun.class));
    }
}
