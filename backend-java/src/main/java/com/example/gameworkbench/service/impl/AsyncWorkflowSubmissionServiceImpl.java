package com.example.gameworkbench.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.application.workflow.WorkflowStepPlan;
import com.example.gameworkbench.application.workflow.WorkflowStepPlanParser;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.enums.WorkflowRunStatus;
import com.example.gameworkbench.common.enums.WorkflowStepRunStatus;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.workflow.AsyncWorkflowSubmitRequest;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.service.AsyncWorkflowSubmissionService;
import com.example.gameworkbench.service.PromptVersionService;
import com.example.gameworkbench.service.WorkflowDefinitionVersionService;
import com.example.gameworkbench.service.WorkflowSubmissionGate;
import com.example.gameworkbench.vo.workflow.WorkflowSubmitVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncWorkflowSubmissionServiceImpl implements AsyncWorkflowSubmissionService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final String GAME_CONFIG_SCHEMA_VERSION = "game-config/2.0";

    private final GameProjectMapper gameProjectMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowDefinitionVersionService workflowDefinitionVersionService;
    private final PromptVersionService promptVersionService;
    private final WorkflowStepPlanParser workflowStepPlanParser;
    private final ObjectMapper objectMapper;
    private final AsyncWorkflowSubmitCommandService commandService;
    private final WorkflowSubmissionGate workflowSubmissionGate;

    @Override
    public WorkflowSubmitVO submit(Long userId, String projectUuid, String idempotencyKey, AsyncWorkflowSubmitRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }

        GameProject project = findUserProject(userId, projectUuid);
        String workflowKey = request.getWorkflowKey().trim();
        String fingerprint = fingerprint(workflowKey, request);
        WorkflowRun existing = findExisting(userId, project.getId(), workflowKey, idempotencyKey);
        if (existing != null) {
            return existingResponse(existing, fingerprint);
        }
        WorkflowRun conflictingKey = workflowRunMapper.selectByProjectIdempotencyKey(userId, project.getId(), idempotencyKey);
        if (conflictingKey != null) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        workflowSubmissionGate.checkNewSubmission(userId);

        WorkflowDefinitionVersion definition = workflowDefinitionVersionService.findActiveDefinition(workflowKey);
        if (definition == null || definition.getId() == null || definition.getDefinitionJson() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        List<WorkflowStepPlan> plans;
        try {
            plans = workflowStepPlanParser.parse(definition.getDefinitionJson());
        } catch (IllegalArgumentException exception) {
            log.error("[AsyncWorkflowSubmit] invalid active definition workflowKey={} reason={}", workflowKey, exception.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        String promptSnapshot = promptSnapshot(plans);
        String traceId = UUID.randomUUID().toString();
        WorkflowRun run = pendingRun(userId, project.getId(), workflowKey, idempotencyKey, fingerprint, definition, promptSnapshot, request.getIdea());
        List<WorkflowStepRun> stepRuns = pendingSteps(run, plans, request);
        String eventPayload = eventPayload(run, traceId);
        try {
            commandService.create(run, stepRuns, eventPayload, traceId);
            return response(run, false);
        } catch (DuplicateKeyException exception) {
            WorkflowRun racedRun = findExisting(userId, project.getId(), workflowKey, idempotencyKey);
            if (racedRun == null) {
                throw exception;
            }
            return existingResponse(racedRun, fingerprint);
        }
    }

    private WorkflowSubmitVO existingResponse(WorkflowRun existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return response(existing, true);
    }

    private WorkflowRun findExisting(Long userId, Long projectId, String workflowKey, String idempotencyKey) {
        return workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                .eq(WorkflowRun::getUserId, userId)
                .eq(WorkflowRun::getProjectId, projectId)
                .eq(WorkflowRun::getWorkflowType, workflowKey)
                .eq(WorkflowRun::getIdempotencyKey, idempotencyKey));
    }

    private GameProject findUserProject(Long userId, String projectUuid) {
        GameProject project = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid)
                .eq(GameProject::getUserId, userId));
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private WorkflowRun pendingRun(Long userId, Long projectId, String workflowKey, String idempotencyKey,
            String fingerprint, WorkflowDefinitionVersion definition, String promptSnapshot, String idea) {
        LocalDateTime now = LocalDateTime.now();
        return WorkflowRun.builder()
                .workflowRunUuid(UUID.randomUUID().toString()).projectId(projectId).userId(userId)
                .workflowType(workflowKey).workflowDefinitionVersionId(definition.getId())
                .workflowDefinitionSnapshot(definition.getDefinitionJson()).promptVersionSnapshot(promptSnapshot)
                .schemaVersion(GAME_CONFIG_SCHEMA_VERSION).attempt(1).statusVersion(0L)
                .idempotencyKey(idempotencyKey).requestFingerprint(fingerprint)
                .status(WorkflowRunStatus.PENDING.name()).inputContent(idea)
                .timeTakenMs(0L).createdAt(now).updatedAt(now).build();
    }

    private List<WorkflowStepRun> pendingSteps(WorkflowRun run, List<WorkflowStepPlan> plans, AsyncWorkflowSubmitRequest request) {
        String inputSnapshot = serialize(Map.of("idea", request.getIdea(), "context", request.getContext() == null ? "" : request.getContext()));
        LocalDateTime now = LocalDateTime.now();
        return plans.stream().map(plan -> WorkflowStepRun.builder()
                .stepRunUuid(UUID.randomUUID().toString()).workflowRunUuid(run.getWorkflowRunUuid())
                .definitionVersionId(run.getWorkflowDefinitionVersionId()).stepKey(plan.stepKey()).stepOrder(plan.stepOrder())
                .agentType(plan.agentType().name()).artifactType(plan.artifactType().name())
                .status(WorkflowStepRunStatus.PENDING.name()).attempt(1).inputSnapshot(inputSnapshot)
                .contextSnapshot(serialize(Map.of("dependsOn", plan.dependsOn())))
                .createdAt(now).updatedAt(now).build()).toList();
    }

    private String promptSnapshot(List<WorkflowStepPlan> plans) {
        Map<String, Map<String, Object>> snapshots = new LinkedHashMap<>();
        for (WorkflowStepPlan plan : plans) {
            PromptVersion prompt = promptVersionService.findActiveByAgentType(plan.agentType().name());
            if (prompt == null || prompt.getId() == null || prompt.getVersionUuid() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("promptVersionId", prompt.getId());
            snapshot.put("versionUuid", prompt.getVersionUuid());
            snapshot.put("templateUuid", prompt.getTemplateUuid());
            snapshot.put("version", prompt.getVersion());
            snapshot.put("outputSchemaKey", prompt.getOutputSchemaKey());
            snapshot.put("outputSchemaVersion", prompt.getOutputSchemaVersion());
            snapshots.put(plan.agentType().name(), snapshot);
        }
        return serialize(snapshots);
    }

    private String eventPayload(WorkflowRun run, String traceId) {
        return serialize(Map.of("schemaVersion", 1, "workflowRunUuid", run.getWorkflowRunUuid(),
                "attempt", run.getAttempt(), "traceId", traceId));
    }

    private String fingerprint(String workflowKey, AsyncWorkflowSubmitRequest request) {
        String canonical = serialize(Map.of("workflowKey", workflowKey, "idea", request.getIdea().trim(),
                "context", request.getContext() == null ? "" : request.getContext().trim()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private WorkflowSubmitVO response(WorkflowRun run, boolean reused) {
        return WorkflowSubmitVO.builder().workflowRunUuid(run.getWorkflowRunUuid())
                .status(run.getStatus()).reused(reused).createdAt(run.getCreatedAt()).build();
    }
}
