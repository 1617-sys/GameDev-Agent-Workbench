package com.example.gameworkbench.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.client.GameBuildClient;
import com.example.gameworkbench.client.dto.GameBuildRequest;
import com.example.gameworkbench.client.dto.GameBuildResponse;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

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

            sendEvent(emitter, event("WORKFLOW_STARTED", "RUNNING",
                    "Demo workflow started", request, null));

            WorkflowRunVO.WorkflowStepVO gameConceptStep = runGameConceptStep(userId, request, emitter);
            WorkflowRunVO.WorkflowStepVO coreLoopDesignStep =
                    runCoreLoopDesignStep(userId, request, gameConceptStep, emitter);
            WorkflowRunVO.WorkflowStepVO taskBreakdownStep =
                    runTaskBreakdownStep(userId, request, gameConceptStep, coreLoopDesignStep, emitter);
            WorkflowRunVO.WorkflowStepVO gameConfigStep =
                    runGameConfigStep(userId, request, gameConceptStep, coreLoopDesignStep, taskBreakdownStep, emitter);

            sendEvent(emitter, event("GAME_BUILD", "RUNNING",
                    "Building playable demo URL", request, null));

            GameBuildResponse gameBuildResponse = gameBuildClient.invoke(GameBuildRequest.builder()
                    .userId(userId)
                    .projectUuid(request.getProjectUuid())
                    .title(request.getTitle())
                    .content(request.getIdea())
                    .gameConcept(gameConceptStep.getContent())
                    .coreLoopDesign(coreLoopDesignStep.getContent())
                    .taskBreakdown(taskBreakdownStep.getContent())
                    .gameConfig(gameConfigStep.getContent())
                    .gameConfigArtifactUuid(gameConfigStep.getArtifactUuid())
                    .artifactUuids(List.of(
                            gameConceptStep.getArtifactUuid(),
                            coreLoopDesignStep.getArtifactUuid(),
                            taskBreakdownStep.getArtifactUuid(),
                            gameConfigStep.getArtifactUuid()
                    ))
                    .buildMode("PHASER_DEMO")
                    .build());

            sendEvent(emitter, GameDemoStreamEventVO.builder()
                    .stage("GAME_BUILD")
                    .status("SUCCESS")
                    .message("Playable demo URL generated")
                    .projectUuid(request.getProjectUuid())
                    .artifactUuid(gameConfigStep.getArtifactUuid())
                    .demoUrl(gameBuildResponse.getDemoUrl())
                    .data(gameBuildResponse)
                    .eventTime(LocalDateTime.now())
                    .build());

            sendEvent(emitter, GameDemoStreamEventVO.builder()
                    .stage("COMPLETED")
                    .status("SUCCESS")
                    .message("Demo workflow completed")
                    .projectUuid(request.getProjectUuid())
                    .artifactUuid(gameConfigStep.getArtifactUuid())
                    .demoUrl(gameBuildResponse.getDemoUrl())
                    .data(List.of(gameConceptStep, coreLoopDesignStep, taskBreakdownStep, gameConfigStep))
                    .eventTime(LocalDateTime.now())
                    .build());

            log.info("[DemoStream] completed userId={} projectUuid={} gameConfigArtifactUuid={} demoUrl={}",
                    userId, request.getProjectUuid(), gameConfigStep.getArtifactUuid(), gameBuildResponse.getDemoUrl());
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
        return runAgentStep(1, userId, request, emitter, AgentType.GAME_CONCEPT,
                "Generating game concept", request.getIdea(), request.getContext(), false);
    }

    private WorkflowRunVO.WorkflowStepVO runCoreLoopDesignStep(
            Long userId,
            GameDemoStreamRequest request,
            WorkflowRunVO.WorkflowStepVO gameConceptStep,
            SseEmitter emitter
    ) {
        return runAgentStep(2, userId, request, emitter, AgentType.CORE_LOOP_DESIGN,
                "Designing core loop", request.getIdea(),
                buildStepContext(request.getContext(), gameConceptStep), false);
    }

    private WorkflowRunVO.WorkflowStepVO runTaskBreakdownStep(
            Long userId,
            GameDemoStreamRequest request,
            WorkflowRunVO.WorkflowStepVO gameConceptStep,
            WorkflowRunVO.WorkflowStepVO coreLoopDesignStep,
            SseEmitter emitter
    ) {
        return runAgentStep(3, userId, request, emitter, AgentType.TASK_BREAKDOWN,
                "Breaking down development tasks", request.getIdea(),
                buildStepContext(request.getContext(), gameConceptStep, coreLoopDesignStep), false);
    }

    private WorkflowRunVO.WorkflowStepVO runGameConfigStep(
            Long userId,
            GameDemoStreamRequest request,
            WorkflowRunVO.WorkflowStepVO gameConceptStep,
            WorkflowRunVO.WorkflowStepVO coreLoopDesignStep,
            WorkflowRunVO.WorkflowStepVO taskBreakdownStep,
            SseEmitter emitter
    ) {
        return runAgentStep(4, userId, request, emitter, AgentType.GAME_CONFIG_GENERATE,
                "Generating Phaser game configuration", request.getIdea(),
                buildStepContext(request.getContext(), gameConceptStep, coreLoopDesignStep, taskBreakdownStep), true);
    }

    private WorkflowRunVO.WorkflowStepVO runAgentStep(
            Integer stepOrder,
            Long userId,
            GameDemoStreamRequest request,
            SseEmitter emitter,
            AgentType agentType,
            String message,
            String content,
            String context,
            boolean saveGameConfigOnly
    ) {
        sendEvent(emitter, event(agentType.name(), "RUNNING", message, request, null));

        AgentRunVO agentRun = agentRunService.run(userId, AgentRunRequest.builder()
                .projectUuid(request.getProjectUuid())
                .agentType(agentType)
                .title(request.getTitle())
                .content(content)
                .context(context)
                .build());

        String artifactContent = saveGameConfigOnly
                ? extractGameConfigContent(agentRun.getOutputContent())
                : agentRun.getOutputContent();
        AgentArtifact artifact = createArtifact(agentRun, request.getTitle(), agentType, artifactContent);

        WorkflowRunVO.WorkflowStepVO step = WorkflowRunVO.WorkflowStepVO.builder()
                .stepOrder(stepOrder)
                .agentType(agentType.name())
                .artifactType(agentType.getArtifactType().name())
                .title(request.getTitle())
                .content(artifactContent)
                .agentRunUuid(agentRun.getRunUuid())
                .artifactUuid(artifact.getArtifactUuid())
                .build();

        sendEvent(emitter, event(agentType.name(), "SUCCESS", successMessage(agentType), request, step));

        log.info("[DemoStream] step completed stepOrder={} agentType={} agentRunUuid={} artifactUuid={} timeTakenMs={}",
                stepOrder, agentType, agentRun.getRunUuid(), artifact.getArtifactUuid(), agentRun.getTimeTakenMs());

        return step;
    }

    private AgentArtifact createArtifact(AgentRunVO agentRun, String title, AgentType agentType, String content) {
        LocalDateTime now = LocalDateTime.now();

        AgentArtifact artifact = AgentArtifact.builder()
                .artifactUuid(UUID.randomUUID().toString())
                .projectId(agentRun.getProjectId())
                .agentRunId(agentRun.getId())
                .artifactType(agentType.getArtifactType().name())
                .title(title)
                .content(content)
                .createdAt(now)
                .updatedAt(now)
                .build();
        agentArtifactMapper.insert(artifact);
        return artifact;
    }

    private String extractGameConfigContent(String outputContent) {
        if (outputContent == null || outputContent.isBlank()) {
            return outputContent;
        }
        try {
            JsonNode root = objectMapper.readTree(outputContent);
            JsonNode gameConfig = root.path("game_config");
            if (gameConfig.isMissingNode() || gameConfig.isNull()) {
                gameConfig = root.path("gameConfig");
            }
            if (gameConfig.isMissingNode() || gameConfig.isNull()) {
                return outputContent;
            }
            return objectMapper.writeValueAsString(gameConfig);
        } catch (Exception exception) {
            log.warn("[DemoStream] game config extraction failed, fallback to full output");
            return outputContent;
        }
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
            sendEvent(emitter, event("FAILED", "FAILED", userFacingFailureMessage(message), request, null));
        } catch (Exception ignored) {
            log.warn("[DemoStream] failed to send failed event projectUuid={}", request.getProjectUuid());
        }
    }

    private String successMessage(AgentType agentType) {
        return switch (agentType) {
            case GAME_CONCEPT -> "Game concept generated";
            case CORE_LOOP_DESIGN -> "Core loop designed";
            case TASK_BREAKDOWN -> "Development tasks generated";
            case GAME_CONFIG_GENERATE -> "Game configuration generated";
            default -> "Agent step completed";
        };
    }

    private String userFacingFailureMessage(String message) {
        if (ErrorCode.ACTIVE_PROMPT_TEMPLATE_NOT_FOUND.getMessage().equals(message)) {
            return "Active prompt template missing. Please create ACTIVE templates for GAME_CONCEPT, CORE_LOOP_DESIGN, TASK_BREAKDOWN and GAME_CONFIG_GENERATE.";
        }
        if (ErrorCode.PYTHON_CALL_FAILED.getMessage().equals(message)) {
            return "Failed to call Python Agent. Please check that python-agent is running at 127.0.0.1:8000 and test the target /agent endpoint first.";
        }
        if (ErrorCode.SYSTEM_ERROR.getMessage().equals(message)) {
            return "Internal server error. Please check Java logs for DemoStream or AgentRun errors.";
        }
        return message;
    }
}
