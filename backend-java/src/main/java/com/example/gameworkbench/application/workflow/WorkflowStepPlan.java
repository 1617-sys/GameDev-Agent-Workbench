package com.example.gameworkbench.application.workflow;

import java.util.List;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;

public record WorkflowStepPlan(
        String stepKey,
        int stepOrder,
        AgentType agentType,
        ArtifactType artifactType,
        List<String> dependsOn
) {
    public WorkflowStepPlan {
        dependsOn = List.copyOf(dependsOn);
    }
}
