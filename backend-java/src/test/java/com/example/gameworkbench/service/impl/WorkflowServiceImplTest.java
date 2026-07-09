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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.dto.workflow.WorkflowRunRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.service.AgentRunService;
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
                agentRunService
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
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class)))
                .thenReturn(agentRun(101L, "run-game-concept", "concept output"))
                .thenReturn(agentRun(102L, "run-core-loop", "core loop output"))
                .thenReturn(agentRun(103L, "run-task-breakdown", "task breakdown output"));

        AtomicReference<WorkflowRun> insertedWorkflow = captureInsertedWorkflow();
        AtomicReference<WorkflowRun> updatedWorkflow = captureUpdatedWorkflow();
        List<AgentArtifact> insertedArtifacts = captureInsertedArtifacts();

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

        assertThat(updatedWorkflow.get().getStatus()).isEqualTo(AgentRunStatus.SUCCESS.name());
        assertThat(updatedWorkflow.get().getErrorMessage()).isNull();
        assertThat(updatedWorkflow.get().getSummary())
                .isEqualTo("Game design workflow completed. Generated 3 artifacts.");
        assertThat(updatedWorkflow.get().getTimeTakenMs()).isNotNegative();
        assertThat(updatedWorkflow.get().getUpdatedAt()).isNotNull();

        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.SUCCESS.name());
        assertThat(result.getSummary()).isEqualTo(updatedWorkflow.get().getSummary());
        assertThat(result.getSteps())
                .extracting(WorkflowRunVO.WorkflowStepVO::getAgentRunUuid)
                .containsExactly("run-game-concept", "run-core-loop", "run-task-breakdown");
    }

    @Test
    void shouldMarkWorkflowFailedWhenAgentStepThrowsBusinessException() {
        WorkflowRunRequest request = workflowRequest();
        BusinessException originalException = new BusinessException(50001, "core loop rejected");
        when(gameProjectMapper.selectOne(any())).thenReturn(ownedProject());
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class)))
                .thenReturn(agentRun(101L, "run-game-concept", "concept output"))
                .thenThrow(originalException);

        AtomicReference<WorkflowRun> insertedWorkflow = captureInsertedWorkflow();
        AtomicReference<WorkflowRun> updatedWorkflow = captureUpdatedWorkflow();
        List<AgentArtifact> insertedArtifacts = captureInsertedArtifacts();

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
    }

    @Test
    void shouldConvertUnexpectedExceptionToSystemError() {
        WorkflowRunRequest request = workflowRequest();
        when(gameProjectMapper.selectOne(any())).thenReturn(ownedProject());
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class)))
                .thenThrow(new IllegalStateException("jdbc password leaked"));

        AtomicReference<WorkflowRun> insertedWorkflow = captureInsertedWorkflow();
        AtomicReference<WorkflowRun> updatedWorkflow = captureUpdatedWorkflow();

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

        verifyNoInteractions(gameProjectMapper, workflowRunMapper, agentArtifactMapper, agentRunService);
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
        verifyNoInteractions(agentArtifactMapper, agentRunService);
    }

    private AtomicReference<WorkflowRun> captureInsertedWorkflow() {
        AtomicReference<WorkflowRun> insertedWorkflow = new AtomicReference<>();
        when(workflowRunMapper.insert(any(WorkflowRun.class))).thenAnswer(invocation -> {
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

    private WorkflowRun copyWorkflowRun(WorkflowRun source) {
        return WorkflowRun.builder()
                .id(source.getId())
                .workflowRunUuid(source.getWorkflowRunUuid())
                .projectId(source.getProjectId())
                .userId(source.getUserId())
                .workflowType(source.getWorkflowType())
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
                .artifactType(source.getArtifactType())
                .title(source.getTitle())
                .content(source.getContent())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .deleted(source.getDeleted())
                .build();
    }
}
