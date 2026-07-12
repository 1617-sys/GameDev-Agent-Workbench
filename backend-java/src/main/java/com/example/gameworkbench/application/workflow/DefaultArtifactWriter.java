package com.example.gameworkbench.application.workflow;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.evaluation.SchemaEvaluationService;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;

@Component
public class DefaultArtifactWriter implements ArtifactWriter {
    private final AgentArtifactMapper artifactMapper;
    private final WorkflowRunEventRecorder workflowRunEventRecorder;
    private final SchemaEvaluationService schemaEvaluationService;
    public DefaultArtifactWriter(AgentArtifactMapper artifactMapper) { this(artifactMapper, (a, b, c, d, e, f, g, h) -> null, null); }
    public DefaultArtifactWriter(AgentArtifactMapper artifactMapper, WorkflowRunEventRecorder workflowRunEventRecorder) {
        this(artifactMapper, workflowRunEventRecorder, null);
    }
    @Autowired
    public DefaultArtifactWriter(AgentArtifactMapper artifactMapper, WorkflowRunEventRecorder workflowRunEventRecorder, SchemaEvaluationService schemaEvaluationService) {
        this.artifactMapper = artifactMapper; this.workflowRunEventRecorder = workflowRunEventRecorder; this.schemaEvaluationService = schemaEvaluationService;
    }
    @Override public StepOutput write(WorkflowExecutionContext context, WorkflowStepPlan plan, WorkflowStepRun stepRun, StepExecutionResult result) {
        String uuid = UUID.randomUUID().toString(); LocalDateTime now = LocalDateTime.now();
        WorkflowEvaluationResult evaluation = result.evaluation();
        String content = evaluation.normalizedContent() == null ? result.output().content() : evaluation.normalizedContent();
        AgentArtifact artifact = AgentArtifact.builder().artifactUuid(uuid).projectId(context.workflowRun().getProjectId())
                .agentRunId(result.agentRunId()).stepRunId(stepRun.getId()).artifactType(plan.artifactType().name())
                .title(plan.stepKey()).content(content).schemaKey(evaluation.schemaKey()).schemaVersion(evaluation.schemaVersion())
                .validationSummary(evaluation.summary()).createdAt(now).updatedAt(now).build();
        artifactMapper.insert(artifact);
        if (schemaEvaluationService != null && evaluation.schemaKey() != null) schemaEvaluationService.evaluateAndPersist(artifact);
        workflowRunEventRecorder.record(context.workflowRun().getWorkflowRunUuid(), "artifact.available",
                "artifact." + uuid + ".available", plan.stepKey(), "AVAILABLE", stepRun.getAttempt(), uuid,
                context.workflowRun().getTraceId());
        return new StepOutput(content, uuid, evaluation.schemaKey(), evaluation.schemaVersion());
    }
}
