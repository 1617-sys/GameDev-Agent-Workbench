package com.example.gameworkbench.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.application.workflow.WorkflowExecutionListener;
import com.example.gameworkbench.application.workflow.WorkflowRunner;
import com.example.gameworkbench.client.GameBuildClient;
import com.example.gameworkbench.client.dto.GameBuildResponse;
import com.example.gameworkbench.dto.demo.GameDemoStreamRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.service.PromptVersionService;
import com.example.gameworkbench.service.RedisService;
import com.example.gameworkbench.service.WorkflowDefinitionVersionService;
import com.example.gameworkbench.vo.demo.GameDemoStreamEventVO;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoStreamServiceImplTest {
    private static final Long USER_ID = 42L;
    private static final String LOCK_KEY = "demoStream:" + USER_ID;
    @Mock WorkflowRunner workflowRunner; @Mock WorkflowRunMapper workflowRuns; @Mock WorkflowStepRunMapper steps;
    @Mock WorkflowDefinitionVersionService definitions; @Mock PromptVersionService prompts; @Mock GameProjectMapper projects;
    @Mock AgentArtifactMapper artifacts; @Mock AgentRunMapper agentRuns; @Mock GameBuildClient gameBuild;
    @Mock RedisService redis;
    private DemoStreamServiceImpl service;
    private GameDemoStreamRequest request;

    @BeforeEach
    void setUp() {
        Executor direct = Runnable::run;
        service = new DemoStreamServiceImpl(direct, workflowRunner, workflowRuns, steps, definitions, prompts, projects,
                artifacts, agentRuns, gameBuild, new ObjectMapper(), redis);
        request = new GameDemoStreamRequest(); request.setProjectUuid("project-uuid"); request.setTitle("Test game"); request.setIdea("idea");
        GameProject project = new GameProject(); project.setId(9L); project.setProjectUuid(request.getProjectUuid()); project.setUserId(USER_ID);
        when(projects.selectOne(any())).thenReturn(project);
        when(definitions.findActiveDefinition("DEMO_GAME_CONFIG")).thenReturn(WorkflowDefinitionVersion.builder().id(8L)
                .definitionJson("{\"steps\":[]}").build());
        when(prompts.findActiveByAgentType(anyString())).thenAnswer(invocation -> PromptVersion.builder().id(1L)
                .versionUuid("version").templateUuid("template").version(1).outputSchemaKey("game-config").outputSchemaVersion("1.0").build());
    }

    @Test
    void delegatesFourStepDemoToRunnerAndPreservesSseOrder() {
        when(redis.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(true);
        List<WorkflowStepRun> fourSteps = steps();
        when(steps.selectByWorkflowRunUuid(anyString())).thenReturn(fourSteps);
        when(artifacts.selectLatestByStepRunId(anyLong())).thenAnswer(invocation -> artifact(invocation.getArgument(0)));
        when(gameBuild.invoke(any())).thenReturn(GameBuildResponse.builder().demoUrl("http://localhost/demo").build());
        doAnswer(invocation -> {
            WorkflowExecutionListener listener = invocation.getArgument(2);
            listener.onEvent("WORKFLOW_STARTED", null);
            for (WorkflowStepRun step : fourSteps) { listener.onEvent("STEP_STARTED", step.getStepKey()); listener.onEvent("STEP_SUCCEEDED", step.getStepKey()); }
            return null;
        }).when(workflowRunner).run(anyString(), eq(request.getProjectUuid()), any());

        try (MockedConstruction<SseEmitter> emitters = Mockito.mockConstruction(SseEmitter.class)) {
            service.streamGameDemo(USER_ID, request);
            List<GameDemoStreamEventVO> events = sentEvents(emitters.constructed().getFirst());
            assertEquals("WORKFLOW_STARTED", events.getFirst().getStage());
            assertTrue(events.stream().anyMatch(event -> "GAME_CONFIG_GENERATE".equals(event.getStage()) && "SUCCESS".equals(event.getStatus())));
            assertEquals("GAME_BUILD", events.get(events.size() - 2).getStage());
            assertEquals("COMPLETED", events.getLast().getStage());
        }
        verify(workflowRunner).run(anyString(), eq(request.getProjectUuid()), any());
        verify(gameBuild).invoke(any());
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(redis).tryLock(eq(LOCK_KEY), owner.capture(), eq(300L)); verify(redis).releaseLock(LOCK_KEY, owner.getValue());
    }

    @Test
    void doesNotRunOrReleaseWhenLockIsNotAcquired() {
        when(redis.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(false);
        service.streamGameDemo(USER_ID, request);
        verify(workflowRunner, never()).run(anyString(), anyString(), any()); verify(gameBuild, never()).invoke(any());
        verify(redis, never()).releaseLock(anyString(), anyString());
    }

    @Test
    void runnerFailureDoesNotBuildAndStillReleasesOwnerLock() {
        when(redis.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(true);
        doThrow(new IllegalStateException("step failed")).when(workflowRunner).run(anyString(), anyString(), any());
        service.streamGameDemo(USER_ID, request);
        verify(gameBuild, never()).invoke(any()); verify(redis).releaseLock(eq(LOCK_KEY), anyString());
    }

    @Test
    void sseSendFailureDoesNotInvokeRunnerTwice() throws Exception {
        when(redis.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(true);
        List<WorkflowStepRun> fourSteps = steps(); when(steps.selectByWorkflowRunUuid(anyString())).thenReturn(fourSteps);
        when(artifacts.selectLatestByStepRunId(anyLong())).thenAnswer(invocation -> artifact(invocation.getArgument(0)));
        when(gameBuild.invoke(any())).thenReturn(GameBuildResponse.builder().demoUrl("url").build());
        doAnswer(invocation -> { invocation.<WorkflowExecutionListener>getArgument(2).onEvent("WORKFLOW_STARTED", null); return null; })
                .when(workflowRunner).run(anyString(), anyString(), any());
        try (MockedConstruction<SseEmitter> emitters = Mockito.mockConstruction(SseEmitter.class,
                (emitter, context) -> doThrow(new IOException("closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            service.streamGameDemo(USER_ID, request);
        }
        verify(workflowRunner).run(anyString(), anyString(), any()); verify(gameBuild).invoke(any());
    }

    private List<WorkflowStepRun> steps() {
        return List.of(step(1L, "game_concept", "GAME_CONCEPT"), step(2L, "core_loop_design", "CORE_LOOP_DESIGN"),
                step(3L, "task_breakdown", "TASK_BREAKDOWN"), step(4L, "game_config_generate", "GAME_CONFIG_GENERATE"));
    }
    private WorkflowStepRun step(Long id, String key, String type) { return WorkflowStepRun.builder().id(id).stepKey(key).agentType(type).agentRunId(id).build(); }
    private AgentArtifact artifact(Long stepId) { return AgentArtifact.builder().id(stepId).artifactUuid("artifact-" + stepId)
            .content(stepId == 4 ? "{\"version\":\"1\"}" : "content-" + stepId).build(); }
    private List<GameDemoStreamEventVO> sentEvents(SseEmitter emitter) {
        ArgumentCaptor<SseEmitter.SseEventBuilder> captured = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        try { verify(emitter, Mockito.atLeastOnce()).send(captured.capture()); } catch (Exception exception) { throw new AssertionError(exception); }
        return captured.getAllValues().stream().flatMap(builder -> builder.build().stream()).map(data -> data.getData())
                .filter(GameDemoStreamEventVO.class::isInstance).map(GameDemoStreamEventVO.class::cast).toList();
    }
}
