package com.example.gameworkbench.application.workflow;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;

@Component
public class DefaultArtifactWriter implements ArtifactWriter {
    private final AgentArtifactMapper artifactMapper;
    public DefaultArtifactWriter(AgentArtifactMapper artifactMapper) { this.artifactMapper = artifactMapper; }
    @Override public StepOutput write(WorkflowExecutionContext context, WorkflowStepPlan plan, WorkflowStepRun stepRun, StepExecutionResult result) {
        String uuid = UUID.randomUUID().toString(); LocalDateTime now = LocalDateTime.now();
        artifactMapper.insert(AgentArtifact.builder().artifactUuid(uuid).projectId(context.workflowRun().getProjectId())
                .agentRunId(result.agentRunId()).stepRunId(stepRun.getId()).artifactType(plan.artifactType().name())
                .title(plan.stepKey()).content(result.output().content()).createdAt(now).updatedAt(now).build());
        return new StepOutput(result.output().content(), uuid, result.output().schemaKey(), result.output().schemaVersion());
    }
}
