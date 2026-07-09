package com.example.gameworkbench.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.client.GameBuildClient;
import com.example.gameworkbench.client.dto.GameBuildResponse;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.dto.demo.GameDemoStreamRequest;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.service.RedisService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import com.example.gameworkbench.vo.demo.GameDemoStreamEventVO;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DemoStreamServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final String LOCK_KEY = "demoStream:" + USER_ID;

    @Mock
    private AgentRunService agentRunService;

    @Mock
    private AgentArtifactMapper agentArtifactMapper;

    @Mock
    private GameBuildClient gameBuildClient;

    @Mock
    private RedisService redisService;

    private DemoStreamServiceImpl demoStreamService;
    private GameDemoStreamRequest request;

    @BeforeEach
    void setUp() {
        Executor sameThreadExecutor = Runnable::run;
        demoStreamService = new DemoStreamServiceImpl(
                sameThreadExecutor,
                agentRunService,
                agentArtifactMapper,
                gameBuildClient,
                new ObjectMapper(),
                redisService
        );

        request = new GameDemoStreamRequest();
        request.setProjectUuid("project-uuid");
        request.setTitle("Lock semantics test");
        request.setIdea("Build a deterministic test game");
        request.setContext("Unit-test context");
    }

    @Test
    @DisplayName("抢锁成功时应执行 Workflow 的第一个 Agent 步骤")
    void shouldExecuteWorkflowWhenLockIsAcquired() {
        when(redisService.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(true);
        when(agentRunService.run(eq(USER_ID), any(AgentRunRequest.class))).thenReturn(AgentRunVO.builder()
                .id(1L)
                .runUuid("run-uuid")
                .projectId(10L)
                .outputContent("{}")
                .timeTakenMs(1L)
                .build());
        when(gameBuildClient.invoke(any())).thenReturn(GameBuildResponse.builder()
                .demoUrl("http://localhost/demo")
                .build());

        demoStreamService.streamGameDemo(USER_ID, request);

        verify(agentRunService).run(eq(USER_ID), argThat(agentRequest ->
                AgentType.GAME_CONCEPT == agentRequest.getAgentType()));
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService).tryLock(eq(LOCK_KEY), ownerCaptor.capture(), eq(300L));
        verify(redisService).releaseLock(LOCK_KEY, ownerCaptor.getValue());
    }

    @Test
    @DisplayName("抢锁失败时应以明确的重复执行结果结束 SSE，且不得执行任何下游")
    void shouldRejectWorkflowWhenLockIsNotAcquired() {
        when(redisService.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(false);

        try (MockedConstruction<SseEmitter> emitters = Mockito.mockConstruction(SseEmitter.class)) {
            demoStreamService.streamGameDemo(USER_ID, request);

            List<GameDemoStreamEventVO> events = sentEvents(emitters.constructed().getFirst());
            assertTrue(events.stream().anyMatch(event ->
                            "FAILED".equals(event.getStatus())
                                    && event.getMessage() != null
                                    && event.getMessage().toLowerCase().contains("already")),
                    "Expected an explicit SSE failure explaining that a workflow is already running");

            verify(agentRunService, never()).run(anyLong(), any(AgentRunRequest.class));
            verify(gameBuildClient, never()).invoke(any());
        }
    }

    @Test
    @DisplayName("抢锁失败的请求不得删除其他请求持有的锁")
    void shouldNotDeleteLockWhenAcquisitionFails() {
        when(redisService.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(false);

        demoStreamService.streamGameDemo(USER_ID, request);

        verify(redisService, never()).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("未认证请求必须在访问 Redis 之前被拒绝")
    void shouldRejectUnauthorizedRequestBeforeAccessingRedis() {
        try (MockedConstruction<SseEmitter> emitters = Mockito.mockConstruction(SseEmitter.class)) {
            demoStreamService.streamGameDemo(null, request);

            List<GameDemoStreamEventVO> events = sentEvents(emitters.constructed().getFirst());
            assertTrue(events.stream().anyMatch(event ->
                            "FAILED".equals(event.getStatus())
                                    && event.getMessage() != null
                                    && event.getMessage().toLowerCase().contains("unauthorized")),
                    "Expected SSE to end with an unauthorized failure");

            verify(redisService, never()).tryLock(anyString(), anyString(), anyLong());
            verify(redisService, never()).releaseLock(anyString(), anyString());
            verify(agentRunService, never()).run(any(), any(AgentRunRequest.class));
        }
    }

    @Test
    @DisplayName("每次请求必须使用唯一 owner token")
    void shouldUseUniqueOwnerTokenForEveryRequest() {
        when(redisService.tryLock(eq(LOCK_KEY), anyString(), eq(300L))).thenReturn(false);

        demoStreamService.streamGameDemo(USER_ID, request);
        demoStreamService.streamGameDemo(USER_ID, request);

        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService, Mockito.times(2)).tryLock(eq(LOCK_KEY), ownerCaptor.capture(), eq(300L));
        assertNotEquals(ownerCaptor.getAllValues().get(0), ownerCaptor.getAllValues().get(1));
    }

    private List<GameDemoStreamEventVO> sentEvents(SseEmitter emitter) {
        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        try {
            verify(emitter, Mockito.atLeastOnce()).send(eventCaptor.capture());
        } catch (Exception exception) {
            throw new AssertionError("Could not inspect emitted SSE events", exception);
        }
        return eventCaptor.getAllValues().stream()
                .flatMap(builder -> builder.build().stream())
                .map(data -> data.getData())
                .filter(GameDemoStreamEventVO.class::isInstance)
                .map(GameDemoStreamEventVO.class::cast)
                .toList();
    }
}
