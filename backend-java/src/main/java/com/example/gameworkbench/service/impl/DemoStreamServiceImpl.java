package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.application.workflow.WorkflowRunner;
import com.example.gameworkbench.client.GameBuildClient;
import com.example.gameworkbench.client.dto.GameBuildRequest;
import com.example.gameworkbench.client.dto.GameBuildResponse;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.enums.WorkflowRunStatus;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.demo.GameDemoStreamRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.service.DemoStreamService;
import com.example.gameworkbench.service.PromptVersionService;
import com.example.gameworkbench.service.RedisService;
import com.example.gameworkbench.service.WorkflowDefinitionVersionService;
import com.example.gameworkbench.vo.demo.GameDemoStreamEventVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Legacy SSE entry point. Workflow orchestration belongs exclusively to {@link WorkflowRunner}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoStreamServiceImpl implements DemoStreamService {
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long DEMO_LOCK_TTL_SECONDS = 300L;
    private static final String DEMO_WORKFLOW = "DEMO_GAME_CONFIG";
    private static final String GAME_CONFIG_SCHEMA_VERSION = "game-config/2.0";

    @Qualifier("demoStreamExecutor") private final Executor demoStreamExecutor;
    private final WorkflowRunner workflowRunner;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final WorkflowDefinitionVersionService workflowDefinitionVersionService;
    private final PromptVersionService promptVersionService;
    private final GameProjectMapper gameProjectMapper;
    private final AgentArtifactMapper agentArtifactMapper;
    private final AgentRunMapper agentRunMapper;
    private final GameBuildClient gameBuildClient;
    private final ObjectMapper objectMapper;
    private final RedisService redisService;

    @Override
    public SseEmitter streamGameDemo(Long userId, GameDemoStreamRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CompletableFuture.runAsync(() -> runDemoStream(userId, request, emitter), demoStreamExecutor);
        return emitter;
    }

    private void runDemoStream(Long userId, GameDemoStreamRequest request, SseEmitter emitter) {
        String lockKey = null;
        String ownerToken = null;
        boolean lockAcquired = false;
        try {
            if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
            lockKey = "demoStream:" + userId;
            ownerToken = UUID.randomUUID().toString();
            lockAcquired = redisService.tryLock(lockKey, ownerToken, DEMO_LOCK_TTL_SECONDS);
            if (!lockAcquired) throw new BusinessException(ErrorCode.DEMO_WORKFLOW_ALREADY_RUNNING);

            GameProject project = getUserProject(userId, request.getProjectUuid());
            WorkflowRun workflowRun = createDemoRun(userId, request, project);
            workflowRunMapper.insert(workflowRun);
            workflowRunner.run(workflowRun.getWorkflowRunUuid(), request.getProjectUuid(),
                    new DemoWorkflowExecutionListener(emitter, request.getProjectUuid(), workflowRun.getWorkflowRunUuid(),
                            workflowStepRunMapper, agentArtifactMapper, agentRunMapper));

            List<WorkflowStepRun> steps = workflowStepRunMapper.selectByWorkflowRunUuid(workflowRun.getWorkflowRunUuid());
            Map<String, AgentArtifact> artifacts = steps.stream().collect(Collectors.toMap(WorkflowStepRun::getStepKey,
                    step -> agentArtifactMapper.selectLatestByStepRunId(step.getId()), (left, right) -> left, LinkedHashMap::new));
            AgentArtifact gameConfig = artifacts.get("game_config_generate");
            String gameConfigContent = requireGameConfig(gameConfig == null ? null : gameConfig.getContent());

            sendEvent(emitter, event("GAME_BUILD", "RUNNING", "Building playable demo URL", request, workflowRun.getWorkflowRunUuid(), null, null, null));
            GameBuildResponse response = gameBuildClient.invoke(GameBuildRequest.builder().userId(userId)
                    .projectUuid(request.getProjectUuid()).title(request.getTitle()).content(request.getIdea())
                    .gameConcept(contentOf(artifacts, "game_concept")).coreLoopDesign(contentOf(artifacts, "core_loop_design"))
                    .taskBreakdown(contentOf(artifacts, "task_breakdown")).gameConfig(gameConfigContent)
                    .gameConfigArtifactUuid(gameConfig.getArtifactUuid()).artifactUuids(artifacts.values().stream()
                            .map(AgentArtifact::getArtifactUuid).toList()).buildMode("PHASER_DEMO").build());
            sendEvent(emitter, event("GAME_BUILD", "SUCCESS", "Playable demo URL generated", request,
                    workflowRun.getWorkflowRunUuid(), null, gameConfig.getArtifactUuid(), response.getDemoUrl()));
            sendEvent(emitter, event("COMPLETED", "SUCCESS", "Demo workflow completed", request,
                    workflowRun.getWorkflowRunUuid(), null, gameConfig.getArtifactUuid(), response.getDemoUrl()));
            emitter.complete();
        } catch (BusinessException exception) {
            log.warn("[DemoStream] failed userId={} projectUuid={} message={}", userId, request.getProjectUuid(), exception.getMessage());
            sendFailedEvent(emitter, request, exception.getMessage()); emitter.complete();
        } catch (Exception exception) {
            log.error("[DemoStream] exception userId={} projectUuid={}", userId, request.getProjectUuid(), exception);
            sendFailedEvent(emitter, request, ErrorCode.SYSTEM_ERROR.getMessage()); emitter.complete();
        } finally {
            if (lockAcquired) {
                try { redisService.releaseLock(lockKey, ownerToken); }
                catch (Exception exception) { log.error("[DemoStream] failed to release lock key={}", lockKey, exception); }
            }
        }
    }

    private WorkflowRun createDemoRun(Long userId, GameDemoStreamRequest request, GameProject project) {
        WorkflowDefinitionVersion definition = workflowDefinitionVersionService.findActiveDefinition(DEMO_WORKFLOW);
        if (definition == null || definition.getId() == null || definition.getDefinitionJson() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        WorkflowRun run = WorkflowRun.builder().workflowRunUuid(UUID.randomUUID().toString()).projectId(project.getId())
                .userId(userId).workflowType(DEMO_WORKFLOW).workflowDefinitionVersionId(definition.getId())
                .workflowDefinitionSnapshot(definition.getDefinitionJson()).promptVersionSnapshot(promptSnapshot())
                .schemaVersion(GAME_CONFIG_SCHEMA_VERSION).attempt(1).statusVersion(0L)
                .status(WorkflowRunStatus.RUNNING.name()).inputContent(request.getIdea()).createdAt(now).updatedAt(now).build();
        return run;
    }

    private String promptSnapshot() {
        Map<String, Map<String, Object>> snapshots = new LinkedHashMap<>();
        for (AgentType type : List.of(AgentType.GAME_CONCEPT, AgentType.CORE_LOOP_DESIGN, AgentType.TASK_BREAKDOWN, AgentType.GAME_CONFIG_GENERATE)) {
            PromptVersion version = promptVersionService.findActiveByAgentType(type.name());
            if (version == null || version.getId() == null || version.getVersionUuid() == null) throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            snapshots.put(type.name(), Map.of("promptVersionId", version.getId(), "versionUuid", version.getVersionUuid(),
                    "templateUuid", version.getTemplateUuid(), "version", version.getVersion(),
                    "outputSchemaKey", version.getOutputSchemaKey(), "outputSchemaVersion", version.getOutputSchemaVersion()));
        }
        try { return objectMapper.writeValueAsString(snapshots); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }

    private GameProject getUserProject(Long userId, String projectUuid) {
        GameProject project = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid).eq(GameProject::getUserId, userId));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        return project;
    }

    private String requireGameConfig(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode config = root.has("game_config") ? root.get("game_config") : root.has("gameConfig") ? root.get("gameConfig") : root;
            if (config == null || !config.isObject()) throw new IllegalArgumentException("GameConfig must be a JSON object");
            return objectMapper.writeValueAsString(config);
        } catch (Exception exception) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }

    private String contentOf(Map<String, AgentArtifact> artifacts, String key) {
        AgentArtifact artifact = artifacts.get(key);
        if (artifact == null) throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        return artifact.getContent();
    }

    private GameDemoStreamEventVO event(String stage, String status, String message, GameDemoStreamRequest request,
            String workflowRunUuid, String agentRunUuid, String artifactUuid, String demoUrl) {
        return GameDemoStreamEventVO.builder().stage(stage).status(status).message(message).projectUuid(request.getProjectUuid())
                .workflowRunUuid(workflowRunUuid).agentRunUuid(agentRunUuid).artifactUuid(artifactUuid).demoUrl(demoUrl)
                .eventTime(LocalDateTime.now()).build();
    }

    private void sendEvent(SseEmitter emitter, GameDemoStreamEventVO event) {
        try { emitter.send(SseEmitter.event().name("progress").data(event)); }
        catch (Exception exception) { log.warn("[DemoStream] failed to send SSE event stage={} status={}", event.getStage(), event.getStatus(), exception); }
    }

    private void sendFailedEvent(SseEmitter emitter, GameDemoStreamRequest request, String message) {
        sendEvent(emitter, event("FAILED", "FAILED", userFacingFailureMessage(message), request, null, null, null, null));
    }

    private String userFacingFailureMessage(String message) {
        if (ErrorCode.PYTHON_CALL_FAILED.getMessage().equals(message)) return "Failed to call Python Agent. Please check that python-agent is running.";
        if (ErrorCode.SYSTEM_ERROR.getMessage().equals(message)) return "Internal server error. Please check Java logs for DemoStream or AgentRun errors.";
        return message;
    }
}
