package com.example.gameworkbench.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.dto.demo.GameDemoStreamRequest;
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

    @Override
    public SseEmitter streamGameDemo(Long userId, GameDemoStreamRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> runDemoStream(userId, request, emitter), demoStreamExecutor);
        return emitter;
    }

    private void runDemoStream(Long userId, GameDemoStreamRequest request, SseEmitter emitter) {
        try {
            if (userId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }

            log.info("[DemoStream] started userId={} projectUuid={} title={}",
                    userId, request.getProjectUuid(), request.getTitle());

            sendEvent(emitter, event(
                    "WORKFLOW_STARTED",
                    "RUNNING",
                    "Game demo workflow started",
                    request,
                    null
            ));

            WorkflowRunVO.WorkflowStepVO gameConceptStep = runGameConceptStep(userId, request, emitter);
            WorkflowRunVO.WorkflowStepVO coreLoopDesignStep =
                    runCoreLoopDesignStep(userId, request, gameConceptStep, emitter);
            WorkflowRunVO.WorkflowStepVO taskBreakdownStep =
                    runTaskBreakdownStep(userId, request, gameConceptStep, coreLoopDesignStep, emitter);

            sendEvent(emitter, GameDemoStreamEventVO.builder()
                    .stage("COMPLETED")
                    .status("SUCCESS")
                    .message("Game demo workflow completed")
                    .projectUuid(request.getProjectUuid())
                    .demoUrl("http://localhost:5173/demo/mock-game")
                    .data(List.of(gameConceptStep, coreLoopDesignStep, taskBreakdownStep))
                    .eventTime(LocalDateTime.now())
                    .build());

            log.info("[DemoStream] completed userId={} projectUuid={}", userId, request.getProjectUuid());
            emitter.complete();
        } catch (BusinessException exception) {
            log.warn("[DemoStream] failed userId={} projectUuid={} message={}",
                    userId, request.getProjectUuid(), exception.getMessage());
            sendFailedEvent(emitter, request, exception.getMessage());
            emitter.complete();
        } catch (Exception exception) {
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

        WorkflowRunVO.WorkflowStepVO step = WorkflowRunVO.WorkflowStepVO.builder()
                .stepOrder(stepOrder)
                .agentType(agentType.name())
                .artifactType(agentType.getArtifactType().name())
                .title(request.getTitle())
                .content(agentRun.getOutputContent())
                .agentRunUuid(agentRun.getRunUuid())
                .artifactUuid(null)
                .build();

        sendEvent(emitter, event(
                agentType.name(),
                "SUCCESS",
                message + " completed",
                request,
                step
        ));

        log.info("[DemoStream] step completed stepOrder={} agentType={} agentRunUuid={} timeTakenMs={}",
                stepOrder, agentType, agentRun.getRunUuid(), agentRun.getTimeTakenMs());

        return step;
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

    private void sendEvent(SseEmitter emitter, GameDemoStreamEventVO event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(event));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send SSE event", exception);
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
