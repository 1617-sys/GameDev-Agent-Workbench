package com.example.gameworkbench.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.example.gameworkbench.client.GameBuildClient;
import com.example.gameworkbench.client.dto.GameBuildRequest;
import com.example.gameworkbench.client.dto.GameBuildResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.dto.demo.GameDemoStreamRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.service.DemoStreamService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import com.example.gameworkbench.vo.demo.GameDemoStreamEventVO;
import com.example.gameworkbench.vo.workflow.WorkflowRunVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoStreamServiceImpl implements DemoStreamService {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    @Qualifier("demoStreamExecutor")
    private final Executor demoStreamExecutor;

    private final AgentRunService agentRunService;
    private final AgentArtifactMapper agentArtifactMapper;
    private final GameBuildClient gameBuildClient;

    @Override
    public SseEmitter streamGameDemo(Long userId, GameDemoStreamRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> runDemoStream(userId, request, emitter), demoStreamExecutor);
        return emitter;
    }

    /**
     * 执行游戏 Demo 生成工作流，通过 SSE 向客户端实时推送各阶段进度。
     * <p>
     * 工作流按顺序执行三个步骤：游戏概念生成 → 核心玩法设计 → 开发任务拆分。
     * 任一阶段失败均通过 {@link #sendFailedEvent} 通知客户端并终止流程。
     *
     * @param userId  当前用户 ID，为 {@code null} 时将抛出 {@link BusinessException}
     * @param request 游戏 Demo 生成请求，包含项目 UUID、标题和创意描述
     * @param emitter SSE 发射器，用于向客户端推送流式事件，流程结束时调用 {@code complete()}
     */
    private void runDemoStream(Long userId, GameDemoStreamRequest request, SseEmitter emitter) {
        try {
            if (userId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }

            log.info("[DemoStream] started userId={} projectUuid={} title={}",
                    userId, request.getProjectUuid(), request.getTitle());

            /* 通知客户端工作流已启动 */
            sendEvent(emitter, event(
                    "WORKFLOW_STARTED",
                    "RUNNING",
                    "Game demo workflow started",
                    request,
                    null
            ));

            /* 按顺序执行三步流水线：概念 → 核心玩法 → 任务拆分 */
            WorkflowRunVO.WorkflowStepVO gameConceptStep = runGameConceptStep(userId, request, emitter);
            WorkflowRunVO.WorkflowStepVO coreLoopDesignStep =
                    runCoreLoopDesignStep(userId, request, gameConceptStep, emitter);
            WorkflowRunVO.WorkflowStepVO taskBreakdownStep =
                    runTaskBreakdownStep(userId, request, gameConceptStep, coreLoopDesignStep, emitter);

            sendEvent(emitter, event(
                    "GAME_BUILD",
                    "RUNNING",
                    "Building playable game demo",
                    request,
                    null
            ));

            GameBuildResponse gameBuildResponse = gameBuildClient.invoke(GameBuildRequest.builder()
                    .userId(userId)
                    .projectUuid(request.getProjectUuid())
                    .title(request.getTitle())
                    .content(request.getIdea())
                    .gameConcept(gameConceptStep.getContent())
                    .coreLoopDesign(coreLoopDesignStep.getContent())
                    .taskBreakdown(taskBreakdownStep.getContent())
                    .artifactUuids(List.of(gameConceptStep.getArtifactUuid(), coreLoopDesignStep.getArtifactUuid(), taskBreakdownStep.getArtifactUuid()))
                    .buildMode("DEMO")
                    .build());

            sendEvent(emitter, event(
                    "GAME_BUILD",
                    "SUCCESS",
                    "Playable game demo generated",
                    request,
                    gameBuildResponse
            ));


            /* 汇总所有步骤结果，推送完成事件 */
            sendEvent(emitter, GameDemoStreamEventVO.builder()
                    .stage("COMPLETED")
                    .status("SUCCESS")
                    .message("Game demo workflow completed")
                    .projectUuid(request.getProjectUuid())
                    .demoUrl(gameBuildResponse.getDemoUrl())
                    .data(List.of(gameConceptStep, coreLoopDesignStep, taskBreakdownStep))
                    .eventTime(LocalDateTime.now())
                    .build());

            log.info("[DemoStream] completed userId={} projectUuid={} demoUrl={}",
                    userId, request.getProjectUuid(), gameBuildResponse.getDemoUrl());
            emitter.complete();
        } catch (BusinessException exception) {
            /* 业务异常：将具体错误消息透传给客户端 */
            log.warn("[DemoStream] failed userId={} projectUuid={} message={}",
                    userId, request.getProjectUuid(), exception.getMessage());
            sendFailedEvent(emitter, request, exception.getMessage());
            emitter.complete();
        } catch (Exception exception) {
            /* 未知异常：统一使用系统错误消息，避免敏感信息泄漏 */
            log.error("[DemoStream] exception userId={} projectUuid={}",
                    userId, request.getProjectUuid(), exception);
            sendFailedEvent(emitter, request, ErrorCode.SYSTEM_ERROR.getMessage());
            emitter.complete();
        }
    }

    private WorkflowRunVO.WorkflowStepVO runGameConceptStep(
            Long userId,
            GameDemoStreamRequest request,
            SseEmitter emitter
    ) {
        return runAgentStep(
                1,
                userId,
                request,
                emitter,
                AgentType.GAME_CONCEPT,
                "Generating game concept",
                request.getIdea(),
                request.getContext()
        );
    }

    private WorkflowRunVO.WorkflowStepVO runCoreLoopDesignStep(
            Long userId,
            GameDemoStreamRequest request,
            WorkflowRunVO.WorkflowStepVO gameConceptStep,
            SseEmitter emitter
    ) {
        return runAgentStep(
                2,
                userId,
                request,
                emitter,
                AgentType.CORE_LOOP_DESIGN,
                "Designing core gameplay loop",
                request.getIdea(),
                buildStepContext(request.getContext(), gameConceptStep)
        );
    }

    private WorkflowRunVO.WorkflowStepVO runTaskBreakdownStep(
            Long userId,
            GameDemoStreamRequest request,
            WorkflowRunVO.WorkflowStepVO gameConceptStep,
            WorkflowRunVO.WorkflowStepVO coreLoopDesignStep,
            SseEmitter emitter
    ) {
        return runAgentStep(
                3,
                userId,
                request,
                emitter,
                AgentType.TASK_BREAKDOWN,
                "Breaking down development tasks",
                request.getIdea(),
                buildStepContext(request.getContext(), gameConceptStep, coreLoopDesignStep)
        );
    }

    private WorkflowRunVO.WorkflowStepVO runAgentStep(
            Integer stepOrder,
            Long userId,
            GameDemoStreamRequest request,
            SseEmitter emitter,
            AgentType agentType,
            String message,
            String content,
            String context
    ) {
        sendEvent(emitter, event(agentType.name(), "RUNNING", message, request, null));

        AgentRunVO agentRun = agentRunService.run(userId, AgentRunRequest.builder()
                .projectUuid(request.getProjectUuid())
                .agentType(agentType)
                .title(request.getTitle())
                .content(content)
                .context(context)
                .build());

        AgentArtifact artifact = createArtifact(agentRun, request.getTitle(), agentType);

        WorkflowRunVO.WorkflowStepVO step = WorkflowRunVO.WorkflowStepVO.builder()
                .stepOrder(stepOrder)
                .agentType(agentType.name())
                .artifactType(agentType.getArtifactType().name())
                .title(request.getTitle())
                .content(agentRun.getOutputContent())
                .agentRunUuid(agentRun.getRunUuid())
                .artifactUuid(artifact.getArtifactUuid())
                .build();

        sendEvent(emitter, event(
                agentType.name(),
                "SUCCESS",
                message + " completed",
                request,
                step
        ));

        log.info("[DemoStream] step completed stepOrder={} agentType={} agentRunUuid={} artifactUuid={} timeTakenMs={}",
                stepOrder, agentType, agentRun.getRunUuid(), artifact.getArtifactUuid(), agentRun.getTimeTakenMs());

        return step;
    }

    private AgentArtifact createArtifact(AgentRunVO agentRun, String title, AgentType agentType) {
        LocalDateTime now = LocalDateTime.now();

        AgentArtifact artifact = AgentArtifact.builder()
                .artifactUuid(UUID.randomUUID().toString())
                .projectId(agentRun.getProjectId())
                .agentRunId(agentRun.getId())
                .artifactType(agentType.getArtifactType().name())
                .title(title)
                .content(agentRun.getOutputContent())
                .createdAt(now)
                .updatedAt(now)
                .build();
        agentArtifactMapper.insert(artifact);
        return artifact;
    }

    private String buildStepContext(String baseContext, WorkflowRunVO.WorkflowStepVO... previousSteps) {
        StringBuilder builder = new StringBuilder();
        if (baseContext != null && !baseContext.isBlank()) {
            builder.append(baseContext).append("\n\n");
        }
        for (WorkflowRunVO.WorkflowStepVO step : previousSteps) {
            builder.append("Previous step ")
                    .append(step.getAgentType())
                    .append(" output:\n")
                    .append(step.getContent())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private GameDemoStreamEventVO event(
            String stage,
            String status,
            String message,
            GameDemoStreamRequest request,
            Object data
    ) {
        return GameDemoStreamEventVO.builder()
                .stage(stage)
                .status(status)
                .message(message)
                .projectUuid(request.getProjectUuid())
                .data(data)
                .eventTime(LocalDateTime.now())
                .build();
    }

    /**
     * 向客户端推送 SSE 进度事件。
     *
     * @param emitter SSE 发射器，用于向客户端发送事件
     * @param event   待推送的游戏 Demo 流事件对象
     * @throws IllegalStateException 当 SSE 发送过程发生 I/O 异常时抛出
     */
    private void sendEvent(SseEmitter emitter, GameDemoStreamEventVO event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(event));
        } catch (IOException | IllegalStateException exception) {
            log.warn("[DemoStream] failed to send SSE event stage={} status={} message={}",
                    event.getStage(), event.getStatus(), exception.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignored) {
                log.debug("[DemoStream] emitter already completed");
            }
        }
    }

    private void sendFailedEvent(SseEmitter emitter, GameDemoStreamRequest request, String message) {
        try {
            sendEvent(emitter, event("FAILED", "FAILED", message, request, null));
        } catch (Exception ignored) {
            log.warn("[DemoStream] failed to send failed event projectUuid={}", request.getProjectUuid());
        }
    }
}
