package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.enums.WorkflowStepRunStatus;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.dto.workflow.WorkflowRunRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.service.WorkflowDefinitionVersionService;
import com.example.gameworkbench.service.PromptVersionService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import com.example.gameworkbench.vo.workflow.WorkflowRunVO;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long PROJECT_ID = 42L;
    private static final String PROJECT_UUID = "project-owned-by-user";

    @Mock
    private GameProjectMapper gameProjectMapper;

    @Mock
    private WorkflowRunMapper workflowRunMapper;

    @Mock
    private AgentArtifactMapper agentArtifactMapper;

    @Mock
    private AgentRunService agentRunService;

    @Mock
    private WorkflowStepRunMapper workflowStepRunMapper;

    @Mock
    private WorkflowDefinitionVersionService workflowDefinitionVersionService;

    @Mock
    private PromptVersionService promptVersionService;

    @Captor
    private ArgumentCaptor<AgentRunRequest> agentRunRequestCaptor;

    private WorkflowServiceImpl workflowService;
    private Logger workflowLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowServiceImpl(
                gameProjectMapper,
                workflowRunMapper,
                agentArtifactMapper,
                agentRunService,
                workflowStepRunMapper,
                workflowDefinitionVersionService,
                promptVersionService,
                new ObjectMapper()
        );
        workflowLogger = (Logger) LoggerFactory.getLogger(WorkflowServiceImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        workflowLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (workflowLogger != null && logAppender != null) {
            workflowLogger.detachAppender(logAppender);
        }
    }

    @Test
    void shouldCompleteWorkflowWhenAllStepsSucceed() {
        WorkflowRunRequest request = workflowRequest();
        when(gameProjectMapper.selectOne(any())).thenReturn(ownedProject());
        when(workflowDefinitionVersionService.findActiveDefinition("GAME_DESIGN"))
                .thenReturn(defaultDefinition());
        stubActivePromptVersions(11L, "prompt-v1");
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class)))
                .thenReturn(agentRun(101L, "run-game-concept", "concept output"))
                .thenReturn(agentRun(102L, "run-core-loop", "core loop output"))
                .thenReturn(agentRun(103L, "run-task-breakdown", "task breakdown output"));

        AtomicReference<WorkflowRun> insertedWorkflow = captureInsertedWorkflow();
        AtomicReference<WorkflowRun> updatedWorkflow = captureUpdatedWorkflow();
        List<AgentArtifact> insertedArtifacts = captureInsertedArtifacts();
        List<WorkflowStepRun> insertedStepRuns = captureInsertedStepRuns();
        List<WorkflowStepRun> updatedStepRuns = captureUpdatedStepRuns();

        WorkflowRunVO result = workflowService.run(USER_ID, request);

        assertThat(insertedWorkflow.get())
                .extracting(
                        WorkflowRun::getProjectId,
                        WorkflowRun::getUserId,
                        WorkflowRun::getStatus,
                        WorkflowRun::getWorkflowType,
                        WorkflowRun::getInputContent,
                        WorkflowRun::getTimeTakenMs
                )
                .containsExactly(
                        PROJECT_ID,
                        USER_ID,
                        AgentRunStatus.RUNNING.name(),
                        "GAME_DESIGN",
                        request.getIdea(),
                        0L
                );
        assertThat(insertedWorkflow.get().getWorkflowRunUuid()).isNotBlank();
        assertThat(insertedWorkflow.get().getCreatedAt()).isNotNull();
        assertThat(insertedWorkflow.get().getUpdatedAt()).isNotNull();
        assertThat(insertedWorkflow.get())
                .extracting(
                        WorkflowRun::getWorkflowDefinitionVersionId,
                        WorkflowRun::getWorkflowDefinitionSnapshot,
                        WorkflowRun::getSchemaVersion,
                        WorkflowRun::getAttempt,
                        WorkflowRun::getStatusVersion
                )
                .containsExactly(900L, "{\"workflowKey\":\"GAME_DESIGN\",\"version\":1}",
                        "game-config/1.0", 1, 0L);
        assertThat(insertedWorkflow.get().getPromptVersionSnapshot())
                .contains("GAME_CONCEPT", "CORE_LOOP_DESIGN", "TASK_BREAKDOWN", "prompt-v1")
                .doesNotContain("systemPrompt", "userPromptTemplate");

        verify(agentRunService, times(3)).run(eq(USER_ID), agentRunRequestCaptor.capture());
        assertThat(agentRunRequestCaptor.getAllValues())
                .extracting(AgentRunRequest::getAgentType)
                .containsExactly(
                        AgentType.GAME_CONCEPT,
                        AgentType.CORE_LOOP_DESIGN,
                        AgentType.TASK_BREAKDOWN
                );

        assertThat(insertedArtifacts)
                .extracting(AgentArtifact::getAgentRunId)
                .containsExactly(101L, 102L, 103L);
        assertThat(insertedArtifacts)
                .extracting(AgentArtifact::getArtifactType)
                .containsExactly(
                        AgentType.GAME_CONCEPT.getArtifactType().name(),
                        AgentType.CORE_LOOP_DESIGN.getArtifactType().name(),
                        AgentType.TASK_BREAKDOWN.getArtifactType().name()
                );
        assertThat(insertedArtifacts)
                .extracting(AgentArtifact::getProjectId)
                .containsExactly(PROJECT_ID, PROJECT_ID, PROJECT_ID);
        assertThat(insertedArtifacts)
                .extracting(AgentArtifact::getStepRunId)
                .containsExactly(100L, 101L, 102L);

        assertThat(insertedStepRuns)
                .extracting(
                        WorkflowStepRun::getWorkflowRunId,
                        WorkflowStepRun::getDefinitionVersionId,
                        WorkflowStepRun::getStepKey,
                        WorkflowStepRun::getStatus,
                        WorkflowStepRun::getAttempt
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(500L, 900L, "game_concept", "RUNNING", 1),
                        org.assertj.core.groups.Tuple.tuple(500L, 900L, "core_loop_design", "RUNNING", 1),
                        org.assertj.core.groups.Tuple.tuple(500L, 900L, "task_breakdown", "RUNNING", 1)
                );
        assertThat(updatedStepRuns)
                .extracting(WorkflowStepRun::getStatus)
                .containsExactly(
                        WorkflowStepRunStatus.SUCCESS.name(),
                        WorkflowStepRunStatus.SUCCESS.name(),
                        WorkflowStepRunStatus.SUCCESS.name()
                );
        assertThat(updatedStepRuns)
                .extracting(WorkflowStepRun::getOutputSnapshot)
                .containsExactly("concept output", "core loop output", "task breakdown output");

        assertThat(updatedWorkflow.get().getStatus()).isEqualTo(AgentRunStatus.SUCCESS.name());
        assertThat(updatedWorkflow.get().getErrorMessage()).isNull();
        assertThat(updatedWorkflow.get().getSummary())
                .isEqualTo("Game design workflow completed. Generated 3 artifacts.");
        assertThat(updatedWorkflow.get().getTimeTakenMs()).isNotNegative();
        assertThat(updatedWorkflow.get().getUpdatedAt()).isNotNull();

        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.SUCCESS.name());
        assertThat(result.getSummary()).isEqualTo(updatedWorkflow.get().getSummary());
        assertThat(result)
                .extracting(
                        WorkflowRunVO::getWorkflowDefinitionVersionId,
                        WorkflowRunVO::getSchemaVersion,
                        WorkflowRunVO::getAttempt,
                        WorkflowRunVO::getStatusVersion
                )
                .containsExactly(900L, "game-config/1.0", 1, 0L);
        assertThat(result.getSteps())
                .extracting(WorkflowRunVO.WorkflowStepVO::getAgentRunUuid)
                .containsExactly("run-game-concept", "run-core-loop", "run-task-breakdown");
    }

    @Test
    void shouldMarkWorkflowFailedWhenAgentStepThrowsBusinessException() {
        WorkflowRunRequest request = workflowRequest();
        BusinessException originalException = new BusinessException(50001, "core loop rejected");
        when(gameProjectMapper.selectOne(any())).thenReturn(ownedProject());
        when(workflowDefinitionVersionService.findActiveDefinition("GAME_DESIGN"))
                .thenReturn(defaultDefinition());
        stubActivePromptVersions(11L, "prompt-v1");
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class)))
                .thenReturn(agentRun(101L, "run-game-concept", "concept output"))
                .thenThrow(originalException);

        AtomicReference<WorkflowRun> insertedWorkflow = captureInsertedWorkflow();
        AtomicReference<WorkflowRun> updatedWorkflow = captureUpdatedWorkflow();
        List<AgentArtifact> insertedArtifacts = captureInsertedArtifacts();
        List<WorkflowStepRun> insertedStepRuns = captureInsertedStepRuns();
        List<WorkflowStepRun> updatedStepRuns = captureUpdatedStepRuns();

        Throwable thrown = catchThrowable(() -> workflowService.run(USER_ID, request));

        assertThat(thrown).isSameAs(originalException);
        assertThat(insertedWorkflow.get().getStatus()).isEqualTo(AgentRunStatus.RUNNING.name());
        assertThat(updatedWorkflow.get().getStatus()).isEqualTo(AgentRunStatus.FAILED.name());
        assertThat(updatedWorkflow.get().getErrorMessage()).isEqualTo("core loop rejected");
        assertThat(updatedWorkflow.get().getTimeTakenMs()).isNotNegative();
        assertThat(updatedWorkflow.get().getUpdatedAt()).isNotNull();

        verify(agentRunService, times(2)).run(eq(USER_ID), agentRunRequestCaptor.capture());
        assertThat(agentRunRequestCaptor.getAllValues())
                .extracting(AgentRunRequest::getAgentType)
                .containsExactly(AgentType.GAME_CONCEPT, AgentType.CORE_LOOP_DESIGN);
        assertThat(insertedArtifacts)
                .extracting(AgentArtifact::getAgentRunId)
                .containsExactly(101L);
        assertThat(insertedStepRuns)
                .extracting(WorkflowStepRun::getStepKey)
                .containsExactly("game_concept", "core_loop_design");
        assertThat(updatedStepRuns)
                .extracting(WorkflowStepRun::getStatus, WorkflowStepRun::getErrorMessage)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SUCCESS", null),
                        org.assertj.core.groups.Tuple.tuple("FAILED", "core loop rejected")
                );
    }

    @Test
    void shouldConvertUnexpectedExceptionToSystemError() {
        WorkflowRunRequest request = workflowRequest();
        when(gameProjectMapper.selectOne(any())).thenReturn(ownedProject());
        when(workflowDefinitionVersionService.findActiveDefinition("GAME_DESIGN"))
                .thenReturn(defaultDefinition());
        stubActivePromptVersions(11L, "prompt-v1");
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class)))
                .thenThrow(new IllegalStateException("jdbc password leaked"));

        AtomicReference<WorkflowRun> insertedWorkflow = captureInsertedWorkflow();
        AtomicReference<WorkflowRun> updatedWorkflow = captureUpdatedWorkflow();
        List<WorkflowStepRun> insertedStepRuns = captureInsertedStepRuns();
        List<WorkflowStepRun> updatedStepRuns = captureUpdatedStepRuns();

        assertThatThrownBy(() -> workflowService.run(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SYSTEM_ERROR.getMessage())
                .extracting("code")
                .isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());

        assertThat(insertedWorkflow.get().getStatus()).isEqualTo(AgentRunStatus.RUNNING.name());
        assertThat(updatedWorkflow.get().getStatus()).isEqualTo(AgentRunStatus.FAILED.name());
        assertThat(updatedWorkflow.get().getErrorMessage()).isEqualTo(ErrorCode.SYSTEM_ERROR.getMessage());
        assertThat(updatedWorkflow.get().getErrorMessage()).doesNotContain("jdbc password leaked");
        assertThat(updatedWorkflow.get().getTimeTakenMs()).isNotNegative();
        verifyNoInteractions(agentArtifactMapper);
        assertThat(insertedStepRuns).hasSize(1);
        assertThat(updatedStepRuns)
                .extracting(WorkflowStepRun::getStatus, WorkflowStepRun::getErrorMessage)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        WorkflowStepRunStatus.FAILED.name(), ErrorCode.SYSTEM_ERROR.getMessage()));
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getFormattedMessage())
                            .contains("exceptionType=java.lang.IllegalStateException")
                            .doesNotContain("jdbc password leaked");
                    assertThat(event.getThrowableProxy()).isNull();
                });
    }

    @Test
    void shouldRejectUnauthorizedUser() {
        WorkflowRunRequest request = workflowRequest();

        assertThatThrownBy(() -> workflowService.run(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.UNAUTHORIZED.getMessage())
                .extracting("code")
                .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());

        verifyNoInteractions(
                gameProjectMapper,
                workflowRunMapper,
                agentArtifactMapper,
                agentRunService,
                workflowStepRunMapper,
                workflowDefinitionVersionService
        );
    }

    @Test
    void shouldRejectProjectNotOwnedByUser() {
        WorkflowRunRequest request = workflowRequest();
        when(gameProjectMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> workflowService.run(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PROJECT_NOT_FOUND.getMessage())
                .extracting("code")
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND.getCode());

        verify(workflowRunMapper, never()).insert(any(WorkflowRun.class));
        verify(workflowRunMapper, never()).updateById(any(WorkflowRun.class));
        verifyNoInteractions(
                agentArtifactMapper,
                agentRunService,
                workflowStepRunMapper,
                workflowDefinitionVersionService
        );
    }

    private AtomicReference<WorkflowRun> captureInsertedWorkflow() {
        AtomicReference<WorkflowRun> insertedWorkflow = new AtomicReference<>();
        when(workflowRunMapper.insert(any(WorkflowRun.class))).thenAnswer(invocation -> {
            invocation.<WorkflowRun>getArgument(0).setId(500L);
            insertedWorkflow.set(copyWorkflowRun(invocation.getArgument(0)));
            return 1;
        });
        return insertedWorkflow;
    }

    private AtomicReference<WorkflowRun> captureUpdatedWorkflow() {
        AtomicReference<WorkflowRun> updatedWorkflow = new AtomicReference<>();
        when(workflowRunMapper.updateById(any(WorkflowRun.class))).thenAnswer(invocation -> {
            updatedWorkflow.set(copyWorkflowRun(invocation.getArgument(0)));
            return 1;
        });
        return updatedWorkflow;
    }

    private List<AgentArtifact> captureInsertedArtifacts() {
        List<AgentArtifact> artifacts = new ArrayList<>();
        when(agentArtifactMapper.insert(any(AgentArtifact.class))).thenAnswer(invocation -> {
            artifacts.add(copyArtifact(invocation.getArgument(0)));
            return 1;
        });
        return artifacts;
    }

    @Test
    void shouldReadHistoricalWorkflowRunWhenSnapshotFieldsAreNull() {
        WorkflowRun historicalRun = WorkflowRun.builder()
                .id(500L)
                .workflowRunUuid("historical-workflow")
                .projectId(PROJECT_ID)
                .userId(USER_ID)
                .workflowType("GAME_DESIGN")
                .status(AgentRunStatus.SUCCESS.name())
                .inputContent("historical input")
                .timeTakenMs(10L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(workflowRunMapper.selectOne(any())).thenReturn(historicalRun);
        when(gameProjectMapper.selectById(PROJECT_ID)).thenReturn(ownedProject());

        WorkflowRunVO result = workflowService.getWorkflowRun(USER_ID, "historical-workflow");

        assertThat(result)
                .extracting(
                        WorkflowRunVO::getWorkflowDefinitionVersionId,
                        WorkflowRunVO::getSchemaVersion,
                        WorkflowRunVO::getAttempt,
                        WorkflowRunVO::getStatusVersion
                )
                .containsOnlyNulls();
        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.SUCCESS.name());
    }

    @Test
    void shouldKeepEarlierPromptVersionSnapshotWhenActivePromptChanges() throws Exception {
        WorkflowRunRequest request = workflowRequest();
        when(gameProjectMapper.selectOne(any())).thenReturn(ownedProject());
        when(workflowDefinitionVersionService.findActiveDefinition("GAME_DESIGN"))
                .thenReturn(defaultDefinition());
        when(promptVersionService.findActiveByAgentType(AgentType.GAME_CONCEPT.name()))
                .thenReturn(promptVersion(11L, "prompt-v1", AgentType.GAME_CONCEPT))
                .thenReturn(promptVersion(21L, "prompt-v2", AgentType.GAME_CONCEPT));
        when(promptVersionService.findActiveByAgentType(AgentType.CORE_LOOP_DESIGN.name()))
                .thenReturn(promptVersion(12L, "prompt-v1", AgentType.CORE_LOOP_DESIGN))
                .thenReturn(promptVersion(22L, "prompt-v2", AgentType.CORE_LOOP_DESIGN));
        when(promptVersionService.findActiveByAgentType(AgentType.TASK_BREAKDOWN.name()))
                .thenReturn(promptVersion(13L, "prompt-v1", AgentType.TASK_BREAKDOWN))
                .thenReturn(promptVersion(23L, "prompt-v2", AgentType.TASK_BREAKDOWN));
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class)))
                .thenReturn(agentRun(101L, "first-1", "first output"))
                .thenReturn(agentRun(102L, "first-2", "first output"))
                .thenReturn(agentRun(103L, "first-3", "first output"))
                .thenReturn(agentRun(201L, "second-1", "second output"))
                .thenReturn(agentRun(202L, "second-2", "second output"))
                .thenReturn(agentRun(203L, "second-3", "second output"));

        workflowService.run(USER_ID, request);
        workflowService.run(USER_ID, request);

        ArgumentCaptor<WorkflowRun> workflowCaptor = ArgumentCaptor.forClass(WorkflowRun.class);
        verify(workflowRunMapper, times(2)).insert(workflowCaptor.capture());
        List<WorkflowRun> createdRuns = workflowCaptor.getAllValues();
        String firstSnapshot = createdRuns.get(0).getPromptVersionSnapshot();
        String secondSnapshot = createdRuns.get(1).getPromptVersionSnapshot();

        JsonNode first = new ObjectMapper().readTree(firstSnapshot);
        JsonNode second = new ObjectMapper().readTree(secondSnapshot);
        assertThat(first.get("GAME_CONCEPT").get("promptVersionId").asLong()).isEqualTo(11L);
        assertThat(second.get("GAME_CONCEPT").get("promptVersionId").asLong()).isEqualTo(21L);
        assertThat(firstSnapshot).isNotEqualTo(secondSnapshot);
    }

    private List<WorkflowStepRun> captureInsertedStepRuns() {
        List<WorkflowStepRun> stepRuns = new ArrayList<>();
        when(workflowStepRunMapper.insert(any(WorkflowStepRun.class))).thenAnswer(invocation -> {
            WorkflowStepRun stepRun = invocation.getArgument(0);
            stepRun.setId(100L + stepRuns.size());
            stepRuns.add(copyStepRun(stepRun));
            return 1;
        });
        return stepRuns;
    }

    private List<WorkflowStepRun> captureUpdatedStepRuns() {
        List<WorkflowStepRun> stepRuns = new ArrayList<>();
        when(workflowStepRunMapper.updateById(any(WorkflowStepRun.class))).thenAnswer(invocation -> {
            stepRuns.add(copyStepRun(invocation.getArgument(0)));
            return 1;
        });
        return stepRuns;
    }

    private WorkflowRunRequest workflowRequest() {
        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setProjectUuid(PROJECT_UUID);
        request.setTitle("Cozy roguelite prototype");
        request.setIdea("A cozy dungeon crawler about restoring constellations.");
        request.setContext("Target platform: web.");
        return request;
    }

    private GameProject ownedProject() {
        GameProject project = new GameProject();
        project.setId(PROJECT_ID);
        project.setProjectUuid(PROJECT_UUID);
        project.setUserId(USER_ID);
        project.setName("Owned project");
        return project;
    }

    private AgentRunVO agentRun(Long id, String runUuid, String outputContent) {
        return AgentRunVO.builder()
                .id(id)
                .runUuid(runUuid)
                .userId(USER_ID)
                .projectId(PROJECT_ID)
                .projectUuid(PROJECT_UUID)
                .outputContent(outputContent)
                .status(AgentRunStatus.SUCCESS.name())
                .timeTakenMs(10L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private WorkflowDefinitionVersion defaultDefinition() {
        return WorkflowDefinitionVersion.builder()
                .id(900L)
                .workflowKey("GAME_DESIGN")
                .version(1)
                .status("ACTIVE")
                .definitionJson("{\"workflowKey\":\"GAME_DESIGN\",\"version\":1}")
                .build();
    }

    private void stubActivePromptVersions(Long firstId, String versionPrefix) {
        when(promptVersionService.findActiveByAgentType(AgentType.GAME_CONCEPT.name()))
                .thenReturn(promptVersion(firstId, versionPrefix, AgentType.GAME_CONCEPT));
        when(promptVersionService.findActiveByAgentType(AgentType.CORE_LOOP_DESIGN.name()))
                .thenReturn(promptVersion(firstId + 1, versionPrefix, AgentType.CORE_LOOP_DESIGN));
        when(promptVersionService.findActiveByAgentType(AgentType.TASK_BREAKDOWN.name()))
                .thenReturn(promptVersion(firstId + 2, versionPrefix, AgentType.TASK_BREAKDOWN));
    }

    private PromptVersion promptVersion(Long id, String versionUuid, AgentType agentType) {
        return PromptVersion.builder()
                .id(id)
                .versionUuid(versionUuid + "-" + agentType.name())
                .templateUuid("template-" + agentType.name())
                .agentType(agentType.name())
                .version(1)
                .status("ACTIVE")
                .build();
    }

    private WorkflowRun copyWorkflowRun(WorkflowRun source) {
        return WorkflowRun.builder()
                .id(source.getId())
                .workflowRunUuid(source.getWorkflowRunUuid())
                .projectId(source.getProjectId())
                .userId(source.getUserId())
                .workflowType(source.getWorkflowType())
                .workflowDefinitionVersionId(source.getWorkflowDefinitionVersionId())
                .workflowDefinitionSnapshot(source.getWorkflowDefinitionSnapshot())
                .promptVersionSnapshot(source.getPromptVersionSnapshot())
                .schemaVersion(source.getSchemaVersion())
                .attempt(source.getAttempt())
                .statusVersion(source.getStatusVersion())
                .status(source.getStatus())
                .inputContent(source.getInputContent())
                .summary(source.getSummary())
                .errorMessage(source.getErrorMessage())
                .timeTakenMs(source.getTimeTakenMs())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .deleted(source.getDeleted())
                .build();
    }

    private AgentArtifact copyArtifact(AgentArtifact source) {
        return AgentArtifact.builder()
                .id(source.getId())
                .artifactUuid(source.getArtifactUuid())
                .projectId(source.getProjectId())
                .agentRunId(source.getAgentRunId())
                .stepRunId(source.getStepRunId())
                .artifactType(source.getArtifactType())
                .title(source.getTitle())
                .content(source.getContent())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .deleted(source.getDeleted())
                .build();
    }

    private WorkflowStepRun copyStepRun(WorkflowStepRun source) {
        return WorkflowStepRun.builder()
                .id(source.getId())
                .stepRunUuid(source.getStepRunUuid())
                .workflowRunId(source.getWorkflowRunId())
                .workflowRunUuid(source.getWorkflowRunUuid())
                .definitionVersionId(source.getDefinitionVersionId())
                .stepKey(source.getStepKey())
                .stepOrder(source.getStepOrder())
                .agentType(source.getAgentType())
                .artifactType(source.getArtifactType())
                .status(source.getStatus())
                .attempt(source.getAttempt())
                .inputSnapshot(source.getInputSnapshot())
                .contextSnapshot(source.getContextSnapshot())
                .outputSnapshot(source.getOutputSnapshot())
                .errorMessage(source.getErrorMessage())
                .startedAt(source.getStartedAt())
                .finishedAt(source.getFinishedAt())
                .timeTakenMs(source.getTimeTakenMs())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
