package com.example.gameworkbench.application.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.gameworkbench.common.enums.ArtifactType;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.evaluation.EvaluationOrchestrator;
import com.example.gameworkbench.evaluation.RuntimeCapabilityRegistry;
import com.example.gameworkbench.gameconfig.ResourceManifestContract;
import com.example.gameworkbench.gameconfig.ResourceManifestContractResult;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;

@Component
public class DefaultArtifactWriter implements ArtifactWriter {
    private final AgentArtifactMapper artifactMapper;
    private final WorkflowRunEventRecorder workflowRunEventRecorder;
    private final EvaluationOrchestrator evaluationOrchestrator;
    private final ResourceManifestContract resourceManifestContract;

    public DefaultArtifactWriter(AgentArtifactMapper artifactMapper) {
        this(artifactMapper, noopRecorder(), null, null);
    }

    public DefaultArtifactWriter(AgentArtifactMapper artifactMapper, WorkflowRunEventRecorder workflowRunEventRecorder) {
        this(artifactMapper, workflowRunEventRecorder, null, null);
    }

    @Autowired
    public DefaultArtifactWriter(AgentArtifactMapper artifactMapper, WorkflowRunEventRecorder workflowRunEventRecorder,
            EvaluationOrchestrator evaluationOrchestrator, ResourceManifestContract resourceManifestContract) {
        this.artifactMapper = artifactMapper;
        this.workflowRunEventRecorder = workflowRunEventRecorder;
        this.evaluationOrchestrator = evaluationOrchestrator;
        this.resourceManifestContract = resourceManifestContract;
    }

    @Override
    public StepOutput write(WorkflowExecutionContext context, WorkflowStepPlan plan, WorkflowStepRun stepRun,
            StepExecutionResult result) {
        WorkflowEvaluationResult evaluation = result.evaluation();
        String content = evaluation.normalizedContent() == null ? result.output().content() : evaluation.normalizedContent();
        Persisted primary = persist(context, plan.artifactType().name(), plan.stepKey(), content,
                evaluation.schemaKey(), evaluation.schemaVersion(), evaluation.summary(), result.agentRunId(), stepRun,
                null, null);
        evaluateIfNew(primary);
        record(context, plan.stepKey(), stepRun, primary.artifact());

        if (ArtifactType.GAME_CONFIG == plan.artifactType() && resourceManifestContract != null) {
            if (!Boolean.TRUE.equals(primary.artifact().getRuntimeEligible())) {
                throw new WorkflowEvaluationException(evaluation.passed()
                        ? "GameConfig Artifact is not runtime eligible" : evaluation.summary());
            }
            ResourceManifestContractResult manifest = resourceManifestContract.derive(primary.artifact().getArtifactUuid(),
                    primary.artifact().getContentDigest(), primary.artifact().getContent());
            if (!manifest.valid()) {
                throw new WorkflowEvaluationException("Resource manifest validation failed: " + manifest.violations());
            }
            Persisted companion = persist(context, ArtifactType.RESOURCE_MANIFEST.name(), "resource_manifest",
                    manifest.canonicalContent(), ResourceManifestContract.SCHEMA_KEY,
                    ResourceManifestContract.SCHEMA_VERSION, "Built-in resource manifest derived and validated",
                    result.agentRunId(), stepRun, primary.artifact().getArtifactUuid(), RuntimeCapabilityRegistry.VERSION);
            evaluateIfNew(companion);
            if (!Boolean.TRUE.equals(companion.artifact().getRuntimeEligible())) {
                throw new WorkflowEvaluationException("Resource manifest Artifact is not runtime eligible");
            }
            record(context, "resource_manifest", stepRun, companion.artifact());
        }
        return new StepOutput(content, primary.artifact().getArtifactUuid(), evaluation.schemaKey(), evaluation.schemaVersion());
    }

    private Persisted persist(WorkflowExecutionContext context, String artifactType, String title, String content,
            String schemaKey, String schemaVersion, String validationSummary, Long agentRunId, WorkflowStepRun stepRun,
            String sourceArtifactUuid, String capabilityVersion) {
        int sourceAttempt = stepRun.getAttempt() == null ? 1 : stepRun.getAttempt();
        String digest = digest(content);
        AgentArtifact existing = stepRun.getId() == null ? null
                : artifactMapper.selectByStepTypeAttempt(stepRun.getId(), artifactType, sourceAttempt);
        if (existing != null) {
            if (!digest.equals(existing.getContentDigest())) {
                throw new WorkflowEvaluationException("Artifact retry source conflict for " + artifactType);
            }
            return new Persisted(existing, false);
        }
        LocalDateTime now = LocalDateTime.now();
        AgentArtifact artifact = AgentArtifact.builder()
                .artifactUuid(UUID.randomUUID().toString()).projectId(context.workflowRun().getProjectId())
                .agentRunId(agentRunId).stepRunId(stepRun.getId()).artifactType(artifactType).title(title)
                .content(content).contentDigest(digest).schemaKey(schemaKey).schemaVersion(schemaVersion)
                .validationSummary(validationSummary).sourceAttempt(sourceAttempt).sourceArtifactUuid(sourceArtifactUuid)
                .runtimeCapabilityVersion(capabilityVersion).runtimeEligible(false)
                .createdAt(now).updatedAt(now).build();
        artifactMapper.insert(artifact);
        return new Persisted(artifact, true);
    }

    private void evaluateIfNew(Persisted persisted) {
        if (evaluationOrchestrator != null && persisted.artifact().getSchemaKey() != null
                && (persisted.created() || (!Boolean.TRUE.equals(persisted.artifact().getRuntimeEligible())
                        && persisted.artifact().getLastEvaluationReportId() == null))) {
            evaluationOrchestrator.evaluate(persisted.artifact());
        }
    }

    private void record(WorkflowExecutionContext context, String stepKey, WorkflowStepRun stepRun, AgentArtifact artifact) {
        workflowRunEventRecorder.record(context.workflowRun().getWorkflowRunUuid(), "artifact.available",
                "artifact." + artifact.getArtifactUuid() + ".available", stepKey, "AVAILABLE", stepRun.getAttempt(),
                artifact.getArtifactUuid(), context.workflowRun().getTraceId());
    }

    private String digest(String content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static WorkflowRunEventRecorder noopRecorder() {
        return (a, b, c, d, e, f, g, h) -> null;
    }

    private record Persisted(AgentArtifact artifact, boolean created) {
    }
}
