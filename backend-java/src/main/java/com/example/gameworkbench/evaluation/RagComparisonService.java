package com.example.gameworkbench.evaluation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.EvaluationReport;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.ModelCallMetric;
import com.example.gameworkbench.entity.RetrievalRecord;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.EvaluationReportMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.ModelCallMetricMapper;
import com.example.gameworkbench.mapper.RetrievalRecordMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagComparisonService {

    private final GameProjectMapper projects;
    private final AgentRunMapper runs;
    private final ModelCallMetricMapper metrics;
    private final AgentArtifactMapper artifacts;
    private final EvaluationReportMapper reports;
    private final RetrievalRecordMapper retrievals;

    public RagComparisonReport compare(
            Long userId,
            Long projectId,
            String experimentKey,
            Long promptVersionId,
            String provider,
            String model,
            LocalDateTime from,
            LocalDateTime to,
            boolean includeMock
    ) {
        validate(userId, projectId, experimentKey, promptVersionId, provider, model, from, to);
        authorize(userId, projectId);

        List<AgentRun> selected = runs.selectList(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getProjectId, projectId)
                .eq(AgentRun::getRagExperimentKey, experimentKey)
                .eq(AgentRun::getPromptVersionId, promptVersionId)
                .eq(AgentRun::getProvider, provider)
                .eq(AgentRun::getModelName, model)
                .ge(from != null, AgentRun::getCreatedAt, from)
                .lt(to != null, AgentRun::getCreatedAt, to)
                .eq(AgentRun::getStatus, "SUCCESS"));

        List<AgentRun> mockRuns = selected.stream().filter(this::isMock).toList();
        List<AgentRun> realRuns = selected.stream().filter(run -> !isMock(run)).toList();
        List<String> snapshots = realRuns.stream()
                .filter(run -> Boolean.TRUE.equals(run.getRagEnabled()))
                .map(AgentRun::getRagContextSnapshot)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        long technicalVersions = realRuns.stream()
                .map(run -> run.getRetrievalVersion() + "|" + run.getChunkingVersion() + "|" + run.getEmbeddingModel())
                .distinct()
                .count();
        EvaluationVersions evaluationVersions = evaluationVersions(realRuns);
        long offSamples = realRuns.stream().filter(run -> !Boolean.TRUE.equals(run.getRagEnabled())).count();
        long onSamples = realRuns.stream().filter(run -> Boolean.TRUE.equals(run.getRagEnabled())).count();

        String status;
        if (snapshots.size() > 1 || technicalVersions > 1 || evaluationVersions.mixed()) {
            status = "INCOMPARABLE_VERSION_MIX";
        } else if (offSamples == 0 || onSamples == 0) {
            status = "INSUFFICIENT_SAMPLES";
        } else {
            status = "COMPARABLE";
        }

        AgentRun any = realRuns.stream().findFirst().orElse(null);
        return new RagComparisonReport(
                status,
                experimentKey,
                projectId,
                promptVersionId,
                provider,
                model,
                from,
                to,
                any == null ? null : any.getRetrievalVersion(),
                any == null ? null : any.getChunkingVersion(),
                any == null ? null : any.getEmbeddingModel(),
                snapshots,
                evaluationVersions.labels(),
                stats(realRuns, false, mockRuns.size()),
                stats(realRuns, true, mockRuns.size()),
                includeMock ? stats(mockRuns, false, 0) : null,
                includeMock ? stats(mockRuns, true, 0) : null
        );
    }

    private void validate(Long userId, Long projectId, String experimentKey, Long promptVersionId,
                          String provider, String model, LocalDateTime from, LocalDateTime to) {
        if (userId == null || projectId == null || promptVersionId == null
                || !StringUtils.hasText(experimentKey) || !StringUtils.hasText(provider)
                || !StringUtils.hasText(model) || (from != null && to != null && !from.isBefore(to))) {
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
    }

    private void authorize(Long userId, Long projectId) {
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getId, projectId)
                .eq(GameProject::getUserId, userId));
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private boolean isMock(AgentRun run) {
        return "TRUE".equals(run.getMockState());
    }

    private EvaluationVersions evaluationVersions(List<AgentRun> selected) {
        Map<String, Set<String>> versions = new LinkedHashMap<>();
        for (AgentRun run : selected) {
            for (AgentArtifact artifact : artifactsFor(run)) {
                for (EvaluationReport report : reportsFor(artifact)) {
                    String version = "SCHEMA".equals(report.getEvaluatorType())
                            ? report.getSchemaVersion() : report.getRuleVersion();
                    if (StringUtils.hasText(version)) {
                        versions.computeIfAbsent(report.getEvaluatorType(), ignored -> new LinkedHashSet<>())
                                .add(version);
                    }
                }
            }
        }
        boolean mixed = versions.values().stream().anyMatch(values -> values.size() > 1);
        List<String> labels = versions.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(value -> entry.getKey() + ":" + value))
                .toList();
        return new EvaluationVersions(labels, mixed);
    }

    private RagCohortStats stats(List<AgentRun> all, boolean rag, int excluded) {
        List<AgentRun> cohort = all.stream()
                .filter(run -> Boolean.TRUE.equals(run.getRagEnabled()) == rag)
                .toList();
        List<Long> latencies = new ArrayList<>();
        long inputTokens = 0;
        long outputTokens = 0;
        BigDecimal cost = BigDecimal.ZERO;
        int schema = 0;
        int rule = 0;
        int runtime = 0;
        int evaluated = 0;
        int missingMetrics = 0;
        int missingCosts = 0;
        int covered = 0;
        int empty = 0;
        int failed = 0;

        for (AgentRun run : cohort) {
            ModelCallMetric metric = metrics.selectOne(new LambdaQueryWrapper<ModelCallMetric>()
                    .eq(ModelCallMetric::getAgentRunId, run.getId()));
            if (metric == null) {
                missingMetrics++;
                missingCosts++;
            } else {
                if (metric.getLatencyMs() != null) {
                    latencies.add(metric.getLatencyMs());
                }
                inputTokens += metric.getInputTokens() == null ? 0 : metric.getInputTokens();
                outputTokens += metric.getOutputTokens() == null ? 0 : metric.getOutputTokens();
                if (metric.getEstimatedCost() == null) {
                    missingCosts++;
                } else {
                    cost = cost.add(metric.getEstimatedCost());
                }
            }

            boolean hadEvaluation = false;
            boolean schemaPassed = false;
            boolean rulePassed = false;
            boolean runtimePassed = false;
            for (AgentArtifact artifact : artifactsFor(run)) {
                for (EvaluationReport report : reportsFor(artifact)) {
                    hadEvaluation = true;
                    if ("PASSED".equals(report.getStatus())) {
                        schemaPassed |= "SCHEMA".equals(report.getEvaluatorType());
                        rulePassed |= "RULE".equals(report.getEvaluatorType());
                        runtimePassed |= "RUNTIME".equals(report.getEvaluatorType());
                    }
                }
            }
            if (hadEvaluation) {
                evaluated++;
                schema += schemaPassed ? 1 : 0;
                rule += rulePassed ? 1 : 0;
                runtime += runtimePassed ? 1 : 0;
            }

            if (rag) {
                long records = retrievals.selectCount(new LambdaQueryWrapper<RetrievalRecord>()
                        .eq(RetrievalRecord::getAgentRunId, run.getId()));
                if (records > 0) {
                    covered++;
                } else if ("EMPTY".equals(run.getRagStatus())) {
                    empty++;
                } else {
                    failed++;
                }
            }
        }

        Collections.sort(latencies);
        return new RagCohortStats(
                cohort.size(),
                evaluated,
                rate(schema, evaluated),
                rate(rule, evaluated),
                rate(runtime, evaluated),
                percentile(latencies, .5),
                percentile(latencies, .95),
                inputTokens,
                outputTokens,
                cost,
                missingMetrics,
                missingCosts,
                covered,
                empty,
                failed,
                excluded
        );
    }

    private List<AgentArtifact> artifactsFor(AgentRun run) {
        return artifacts.selectList(new LambdaQueryWrapper<AgentArtifact>()
                .eq(AgentArtifact::getAgentRunId, run.getId()));
    }

    private List<EvaluationReport> reportsFor(AgentArtifact artifact) {
        return reports.selectList(new LambdaQueryWrapper<EvaluationReport>()
                .eq(EvaluationReport::getArtifactId, artifact.getId()));
    }

    private double rate(int passed, int total) {
        return total == 0 ? 0 : (double) passed / total;
    }

    private long percentile(List<Long> values, double percentile) {
        return values.isEmpty() ? 0
                : values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * percentile) - 1));
    }

    private record EvaluationVersions(List<String> labels, boolean mixed) {
    }
}
