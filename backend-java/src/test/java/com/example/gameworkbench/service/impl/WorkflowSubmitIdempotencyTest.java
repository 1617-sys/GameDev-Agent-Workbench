package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gameworkbench.application.workflow.WorkflowStepPlanParser;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.workflow.AsyncWorkflowSubmitRequest;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.service.PromptVersionService;
import com.example.gameworkbench.service.WorkflowDefinitionVersionService;
import com.example.gameworkbench.service.WorkflowSubmissionGate;
import com.example.gameworkbench.vo.workflow.WorkflowSubmitVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class WorkflowSubmitIdempotencyTest {

    @Mock private GameProjectMapper projects;
    @Mock private WorkflowRunMapper runs;
    @Mock private WorkflowDefinitionVersionService definitions;
    @Mock private PromptVersionService prompts;
    @Mock private AsyncWorkflowSubmitCommandService commandService;
    @Mock private WorkflowSubmissionGate submissionGate;

    private AsyncWorkflowSubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AsyncWorkflowSubmissionServiceImpl(projects, runs, definitions, prompts,
                new WorkflowStepPlanParser(objectMapper), objectMapper, commandService, submissionGate);
        lenient().when(projects.selectOne(any())).thenReturn(project());
    }

    @Test
    void createsPendingRunStepsAndOutboxIntentWithoutExecutingRunner() {
        when(runs.selectOne(any())).thenReturn(null);
        when(definitions.findActiveDefinition("GAME_DESIGN")).thenReturn(definition());
        stubPrompts();
        doAnswer(invocation -> {
            WorkflowRun run = invocation.getArgument(0);
            run.setId(17L);
            return run;
        }).when(commandService).create(any(), any(), any(), any());

        WorkflowSubmitVO response = service.submit(7L, "project", "submit-1", request("idea"));

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.isReused()).isFalse();
        ArgumentCaptor<WorkflowRun> runCaptor = ArgumentCaptor.forClass(WorkflowRun.class);
        verify(commandService).create(runCaptor.capture(), org.mockito.ArgumentMatchers.argThat(steps -> steps.size() == 2),
                org.mockito.ArgumentMatchers.contains("workflowRunUuid"), any());
        assertThat(runCaptor.getValue().getSchemaVersion()).isEqualTo("game-config/2.0");
    }

    @Test
    void reusesEquivalentExistingSubmission() {
        AsyncWorkflowSubmitRequest request = request("idea");
        WorkflowRun existing = WorkflowRun.builder().workflowRunUuid("existing").status("PENDING")
                .requestFingerprint(fingerprintFor(request)).build();
        when(runs.selectOne(any())).thenReturn(existing);

        WorkflowSubmitVO response = service.submit(7L, "project", "submit-1", request);

        assertThat(response.getWorkflowRunUuid()).isEqualTo("existing");
        assertThat(response.isReused()).isTrue();
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    @Test
    void rejectsSameKeyWithDifferentRequest() {
        WorkflowRun existing = WorkflowRun.builder().workflowRunUuid("existing").status("PENDING")
                .requestFingerprint("different").build();
        when(runs.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.submit(7L, "project", "submit-1", request("idea")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.IDEMPOTENCY_KEY_CONFLICT.getMessage());
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    @Test
    void rejectsKeyReusedForDifferentWorkflowKey() {
        when(runs.selectOne(any())).thenReturn(null);
        when(runs.selectByProjectIdempotencyKey(7L, 1L, "submit-1"))
                .thenReturn(WorkflowRun.builder().workflowRunUuid("existing").build());
        AsyncWorkflowSubmitRequest request = request("idea");
        request.setWorkflowKey("ANOTHER_WORKFLOW");

        assertThatThrownBy(() -> service.submit(7L, "project", "submit-1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.IDEMPOTENCY_KEY_CONFLICT.getMessage());
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    @Test
    void resolvesDatabaseUniqueRaceByReadingWinner() {
        AsyncWorkflowSubmitRequest request = request("idea");
        WorkflowRun winner = WorkflowRun.builder().workflowRunUuid("winner").status("PENDING")
                .requestFingerprint(fingerprintFor(request)).build();
        when(runs.selectOne(any())).thenReturn(null, winner);
        when(definitions.findActiveDefinition("GAME_DESIGN")).thenReturn(definition());
        stubPrompts();
        when(commandService.create(any(), any(), any(), any())).thenThrow(new DuplicateKeyException("unique"));

        WorkflowSubmitVO response = service.submit(7L, "project", "submit-1", request);

        assertThat(response.getWorkflowRunUuid()).isEqualTo("winner");
        assertThat(response.isReused()).isTrue();
    }

    @Test
    void rejectsMissingOrUnsafeIdempotencyKeyBeforeAnyInsert() {
        assertThatThrownBy(() -> service.submit(7L, "project", " ", request("idea")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.IDEMPOTENCY_KEY_INVALID.getMessage());
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    @Test
    void rateLimitRejectionDoesNotCreateRunStepsOrOutbox() {
        when(runs.selectOne(any())).thenReturn(null);
        doThrow(new BusinessException(ErrorCode.WORKFLOW_RATE_LIMITED)).when(submissionGate).checkNewSubmission(7L);

        assertThatThrownBy(() -> service.submit(7L, "project", "submit-1", request("idea")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.WORKFLOW_RATE_LIMITED.getMessage());
        verify(commandService, never()).create(any(), any(), any(), any());
    }

    private void stubPrompts() {
        for (String type : new String[]{"GAME_CONCEPT", "CORE_LOOP_DESIGN"}) {
            when(prompts.findActiveByAgentType(type)).thenReturn(PromptVersion.builder().id(1L)
                    .versionUuid(type + "-version").build());
        }
    }

    private String fingerprintFor(AsyncWorkflowSubmitRequest request) {
        // Reuse a serial first submission to obtain the canonical persisted fingerprint.
        when(runs.selectOne(any())).thenReturn(null);
        when(definitions.findActiveDefinition("GAME_DESIGN")).thenReturn(definition());
        stubPrompts();
        doAnswer(invocation -> {
            WorkflowRun run = invocation.getArgument(0);
            run.setId(1L);
            return run;
        }).when(commandService).create(any(), any(), any(), any());
        service.submit(7L, "project", "seed-key", request);
        org.mockito.ArgumentCaptor<WorkflowRun> captor = org.mockito.ArgumentCaptor.forClass(WorkflowRun.class);
        verify(commandService).create(captor.capture(), any(), any(), any());
        reset(commandService);
        return captor.getValue().getRequestFingerprint();
    }

    private AsyncWorkflowSubmitRequest request(String idea) {
        AsyncWorkflowSubmitRequest request = new AsyncWorkflowSubmitRequest();
        request.setWorkflowKey("GAME_DESIGN");
        request.setIdea(idea);
        request.setContext("context");
        return request;
    }

    private GameProject project() {
        GameProject project = new GameProject();
        project.setId(1L);
        project.setProjectUuid("project");
        project.setUserId(7L);
        return project;
    }

    private WorkflowDefinitionVersion definition() {
        return WorkflowDefinitionVersion.builder().id(1L).definitionJson("""
                {"steps":[
                  {"stepKey":"concept","stepOrder":1,"agentType":"GAME_CONCEPT","artifactType":"GAME_CONCEPT_RESULT","dependsOn":[]},
                  {"stepKey":"loop","stepOrder":2,"agentType":"CORE_LOOP_DESIGN","artifactType":"CORE_LOOP_DESIGN_RESULT","dependsOn":["concept"]}
                ]}
                """).build();
    }
}
