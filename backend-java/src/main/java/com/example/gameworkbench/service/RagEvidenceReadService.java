package com.example.gameworkbench.service;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.retrieval.RagEvidenceResponse;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.RetrievalRecord;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.evaluation.RagComparisonReport;
import com.example.gameworkbench.evaluation.RagComparisonService;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.RetrievalRecordMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagEvidenceReadService {

    private final WorkflowRunMapper workflowRuns;
    private final WorkflowStepRunMapper workflowSteps;
    private final AgentRunMapper agentRuns;
    private final RetrievalRecordMapper retrievalRecords;
    private final RagComparisonService comparisons;

    public List<RagEvidenceResponse> list(Long userId, String workflowRunUuid) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        WorkflowRun workflowRun = workflowRuns.selectReadModelByUserIdAndWorkflowRunUuid(userId, workflowRunUuid);
        if (workflowRun == null) {
            throw new BusinessException(ErrorCode.WORKFLOW_RUN_NOT_FOUND);
        }
        return workflowSteps.selectByWorkflowRunUuid(workflowRunUuid).stream()
                .filter(step -> step.getAgentRunId() != null)
                .map(step -> evidence(userId, workflowRun, step))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private RagEvidenceResponse evidence(Long userId, WorkflowRun workflowRun, WorkflowStepRun step) {
        AgentRun run = agentRuns.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getId, step.getAgentRunId())
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getProjectId, workflowRun.getProjectId()));
        if (run == null) {
            return null;
        }
        List<RagEvidenceResponse.ReferenceSummary> references = retrievalRecords
                .selectList(new LambdaQueryWrapper<RetrievalRecord>()
                        .eq(RetrievalRecord::getAgentRunId, run.getId())
                        .eq(RetrievalRecord::getProjectId, workflowRun.getProjectId())
                        .orderByAsc(RetrievalRecord::getRankNo))
                .stream()
                .map(record -> new RagEvidenceResponse.ReferenceSummary(
                        record.getDocumentUuid(),
                        record.getDocumentVersion(),
                        record.getChunkUuid(),
                        record.getRankNo(),
                        record.getScore()))
                .toList();
        return new RagEvidenceResponse(
                step.getStepKey(),
                run.getRunUuid(),
                run.getRagEnabled(),
                run.getRagStatus(),
                "TRUE".equals(run.getMockState()),
                run.getContextBudget(),
                run.getRetrievalVersion(),
                run.getChunkingVersion(),
                run.getEmbeddingModel(),
                references,
                comparison(userId, workflowRun.getProjectId(), run)
        );
    }

    private RagEvidenceResponse.ComparisonSummary comparison(Long userId, Long projectId, AgentRun run) {
        if (!StringUtils.hasText(run.getRagExperimentKey()) || run.getPromptVersionId() == null
                || !StringUtils.hasText(run.getProvider()) || !StringUtils.hasText(run.getModelName())
                || run.getCreatedAt() == null) {
            return null;
        }
        LocalDateTime from = run.getCreatedAt().minusDays(30);
        LocalDateTime to = run.getCreatedAt().plusSeconds(1);
        RagComparisonReport report = comparisons.compare(
                userId,
                projectId,
                run.getRagExperimentKey(),
                run.getPromptVersionId(),
                run.getProvider(),
                run.getModelName(),
                from,
                to,
                false
        );
        return new RagEvidenceResponse.ComparisonSummary(
                report.status(),
                report.promptVersionId(),
                report.provider(),
                report.modelName(),
                report.from(),
                report.to(),
                report.retrievalVersion(),
                report.chunkingVersion(),
                report.embeddingModel(),
                report.evaluationVersions(),
                report.ragOff(),
                report.ragOn()
        );
    }
}
