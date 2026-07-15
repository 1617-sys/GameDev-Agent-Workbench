package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.service.WorkflowRunQueryService;
import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;
import com.example.gameworkbench.vo.workflow.WorkflowRunSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowRunQueryServiceImpl implements WorkflowRunQueryService {

    private static final Set<String> CANCELLABLE_STATUSES = Set.of("PENDING", "QUEUED", "RUNNING", "RETRY_WAIT");
    private static final Set<String> RETRYABLE_STATUSES = Set.of("FAILED", "TIMEOUT");

    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final AgentArtifactMapper agentArtifactMapper;

    @Override
    public List<WorkflowRunSummaryVO> listProjectRuns(Long userId, String projectUuid) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (projectUuid == null || projectUuid.isBlank()) {
            return List.of();
        }
        return workflowRunMapper.selectRecentByUserIdAndProjectUuid(userId, projectUuid)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public WorkflowRunDetailVO getRun(Long userId, String workflowRunUuid) {
        WorkflowRun run = requireOwnedRun(userId, workflowRunUuid);
        List<WorkflowStepRun> steps = workflowStepRunMapper.selectReadModelByWorkflowRunUuid(run.getWorkflowRunUuid());
        List<WorkflowRunDetailVO.ArtifactSummaryVO> artifacts = loadArtifacts(steps);
        return toDetail(run, steps, artifacts);
    }

    @Override
    public List<WorkflowRunDetailVO.WorkflowStepReadVO> getSteps(Long userId, String workflowRunUuid) {
        WorkflowRun run = requireOwnedRun(userId, workflowRunUuid);
        return workflowStepRunMapper.selectReadModelByWorkflowRunUuid(run.getWorkflowRunUuid())
                .stream().map(this::toStep).toList();
    }

    @Override
    public List<WorkflowRunDetailVO.ArtifactSummaryVO> getArtifacts(Long userId, String workflowRunUuid) {
        WorkflowRun run = requireOwnedRun(userId, workflowRunUuid);
        return loadArtifacts(workflowStepRunMapper.selectReadModelByWorkflowRunUuid(run.getWorkflowRunUuid()));
    }

    private WorkflowRun requireOwnedRun(Long userId, String workflowRunUuid) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        WorkflowRun run = workflowRunMapper.selectReadModelByUserIdAndWorkflowRunUuid(userId, workflowRunUuid);
        if (run == null) {
            // Use the same response for an unknown run and a run owned by another user.
            throw new BusinessException(ErrorCode.WORKFLOW_RUN_NOT_FOUND);
        }
        return run;
    }

    private WorkflowRunDetailVO toDetail(
            WorkflowRun run,
            List<WorkflowStepRun> steps,
            List<WorkflowRunDetailVO.ArtifactSummaryVO> artifacts
    ) {
        return WorkflowRunDetailVO.builder()
                .workflowRunUuid(run.getWorkflowRunUuid())
                .status(run.getStatus())
                .attempt(run.getAttempt())
                .definitionVersionId(run.getWorkflowDefinitionVersionId())
                .schemaVersion(run.getSchemaVersion())
                .statusVersion(run.getStatusVersion())
                .lastSequence(run.getEventSequence())
                .timeTakenMs(run.getTimeTakenMs())
                .createdAt(run.getCreatedAt())
                .updatedAt(run.getUpdatedAt())
                .failedAt(run.getFailedAt())
                .error(toError(run.getLastErrorCode(), run.getStatus()))
                .allowedActions(allowedActions(run.getStatus()))
                .steps(steps.stream().map(this::toStep).toList())
                .artifacts(artifacts)
                .build();
    }

    private WorkflowRunSummaryVO toSummary(WorkflowRun run) {
        return WorkflowRunSummaryVO.builder()
                .workflowRunUuid(run.getWorkflowRunUuid())
                .workflowType(run.getWorkflowType())
                .status(run.getStatus())
                .attempt(run.getAttempt())
                .timeTakenMs(run.getTimeTakenMs())
                .createdAt(run.getCreatedAt())
                .updatedAt(run.getUpdatedAt())
                .build();
    }

    private List<WorkflowRunDetailVO.ArtifactSummaryVO> loadArtifacts(List<WorkflowStepRun> steps) {
        Map<Long, String> stepKeys = steps.stream()
                .filter(step -> step.getId() != null)
                .collect(Collectors.toMap(WorkflowStepRun::getId, WorkflowStepRun::getStepKey));
        if (stepKeys.isEmpty()) {
            return List.of();
        }
        return agentArtifactMapper.selectReadModelByStepRunIds(stepKeys.keySet()).stream()
                .map(artifact -> toArtifact(artifact, stepKeys.get(artifact.getStepRunId())))
                .toList();
    }

    private WorkflowRunDetailVO.WorkflowStepReadVO toStep(WorkflowStepRun step) {
        return WorkflowRunDetailVO.WorkflowStepReadVO.builder()
                .stepKey(step.getStepKey())
                .stepOrder(step.getStepOrder())
                .agentType(step.getAgentType())
                .artifactType(step.getArtifactType())
                .status(step.getStatus())
                .attempt(step.getAttempt())
                .schemaKey(step.getSchemaKey())
                .schemaVersion(step.getSchemaVersion())
                .timeTakenMs(step.getTimeTakenMs())
                .startedAt(step.getStartedAt())
                .finishedAt(step.getFinishedAt())
                .createdAt(step.getCreatedAt())
                .updatedAt(step.getUpdatedAt())
                .error(toError(null, step.getStatus()))
                .build();
    }

    private WorkflowRunDetailVO.ArtifactSummaryVO toArtifact(AgentArtifact artifact, String stepKey) {
        return WorkflowRunDetailVO.ArtifactSummaryVO.builder()
                .artifactUuid(artifact.getArtifactUuid())
                .stepKey(stepKey)
                .type(artifact.getArtifactType())
                .displayName(artifact.getTitle())
                .status("AVAILABLE")
                .contentDigest(artifact.getContentDigest())
                .schemaKey(artifact.getSchemaKey())
                .schemaVersion(artifact.getSchemaVersion())
                .validationSummary(artifact.getValidationSummary())
                .sourceAttempt(artifact.getSourceAttempt())
                .sourceArtifactUuid(artifact.getSourceArtifactUuid())
                .runtimeCapabilityVersion(artifact.getRuntimeCapabilityVersion())
                .runtimeEligible(artifact.getRuntimeEligible())
                .url("/api/artifacts/" + artifact.getArtifactUuid())
                .createdAt(artifact.getCreatedAt())
                .build();
    }

    private WorkflowRunDetailVO.ErrorSummaryVO toError(String persistedCode, String status) {
        if (!"FAILED".equals(status) && !"TIMEOUT".equals(status)) {
            return null;
        }
        String code = persistedCode == null || persistedCode.isBlank() ? "WORKFLOW_EXECUTION_FAILED" : persistedCode;
        String message = "TIMEOUT".equals(status)
                ? "Workflow execution timed out"
                : "Workflow execution failed";
        return WorkflowRunDetailVO.ErrorSummaryVO.builder().code(code).message(message).build();
    }

    private List<String> allowedActions(String status) {
        if (CANCELLABLE_STATUSES.contains(status)) {
            return List.of("cancel");
        }
        if (RETRYABLE_STATUSES.contains(status)) {
            return List.of("retry");
        }
        return List.of();
    }
}
