package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.gameworkbench.application.workflow.WorkflowRunner;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.workflow.WorkflowRunRequest;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.*;
import com.example.gameworkbench.service.*;
import com.example.gameworkbench.vo.workflow.WorkflowRunVO;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {
    @Mock GameProjectMapper projects; @Mock WorkflowRunMapper runs; @Mock AgentArtifactMapper artifacts;
    @Mock AgentRunService agentRuns; @Mock WorkflowStepRunMapper steps; @Mock WorkflowDefinitionVersionService definitions;
    @Mock PromptVersionService prompts; @Mock WorkflowRunner runner; @Mock AgentRunMapper agentRunMapper;
    private WorkflowServiceImpl service;
    @BeforeEach void setup() { service = new WorkflowServiceImpl(projects, runs, artifacts, agentRuns, steps, definitions, prompts, new ObjectMapper(), runner, agentRunMapper); }

    @Test void shouldDelegateLegacyRunToRunner() {
        GameProject project = project(); when(projects.selectOne(any())).thenReturn(project); when(definitions.findActiveDefinition("GAME_DESIGN")).thenReturn(definition()); stubPrompts();
        doAnswer(i -> { WorkflowRun run = i.getArgument(0); run.setId(1L); return 1; }).when(runs).insert(any(WorkflowRun.class));
        when(runs.selectOne(any())).thenReturn(WorkflowRun.builder().workflowRunUuid("persisted").status("SUCCESS").build());
        when(steps.selectByWorkflowRunUuid(any())).thenReturn(List.of());
        WorkflowRunVO result = service.run(7L, request());
        verify(runner).run(any(), eq("project"), any()); verifyNoInteractions(agentRuns);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    @Test void shouldMapRunnerFailureToLegacySystemError() {
        when(projects.selectOne(any())).thenReturn(project()); when(definitions.findActiveDefinition("GAME_DESIGN")).thenReturn(definition()); stubPrompts();
        doThrow(new IllegalStateException("runner failed")).when(runner).run(any(), any(), any());
        assertThatThrownBy(() -> service.run(7L, request())).isInstanceOf(BusinessException.class).hasMessage(ErrorCode.SYSTEM_ERROR.getMessage());
    }

    @Test void shouldRejectUnauthorizedBeforeRunner() {
        assertThatThrownBy(() -> service.run(null, request())).isInstanceOf(BusinessException.class).hasMessage(ErrorCode.UNAUTHORIZED.getMessage());
        verifyNoInteractions(runner, projects);
    }

    @Test void shouldReadHistoricalRunWithoutExecutingRunner() {
        WorkflowRun run = WorkflowRun.builder().workflowRunUuid("run").projectId(1L).userId(7L).workflowType("GAME_DESIGN").status("SUCCESS").inputContent("i").build();
        when(runs.selectOne(any())).thenReturn(run); when(projects.selectById(1L)).thenReturn(project());
        assertThat(service.getWorkflowRun(7L, "run").getStatus()).isEqualTo("SUCCESS"); verifyNoInteractions(runner);
    }

    private void stubPrompts() { for (String type : List.of("GAME_CONCEPT", "CORE_LOOP_DESIGN", "TASK_BREAKDOWN")) when(prompts.findActiveByAgentType(type)).thenReturn(PromptVersion.builder().id(1L).versionUuid(type).build()); }
    private WorkflowDefinitionVersion definition() { return WorkflowDefinitionVersion.builder().id(1L).definitionJson("{\"steps\":[]}").build(); }
    private GameProject project() { GameProject p = new GameProject(); p.setId(1L); p.setProjectUuid("project"); p.setUserId(7L); return p; }
    private WorkflowRunRequest request() { WorkflowRunRequest r = new WorkflowRunRequest(); r.setProjectUuid("project"); r.setIdea("idea"); return r; }
}
