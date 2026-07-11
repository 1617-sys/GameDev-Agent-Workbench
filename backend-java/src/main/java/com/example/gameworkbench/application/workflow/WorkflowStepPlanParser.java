package com.example.gameworkbench.application.workflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class WorkflowStepPlanParser {

    private final ObjectMapper objectMapper;

    public WorkflowStepPlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<WorkflowStepPlan> parse(String definitionSnapshot) {
        try {
            JsonNode root = objectMapper.readTree(definitionSnapshot);
            JsonNode steps = root.path("steps");
            if (!steps.isArray() || steps.isEmpty()) {
                throw new IllegalArgumentException("Workflow definition snapshot must contain a non-empty steps array");
            }

            List<WorkflowStepPlan> plans = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (JsonNode step : steps) {
                String stepKey = requiredText(step, "stepKey");
                if (!keys.add(stepKey)) {
                    throw new IllegalArgumentException("Workflow definition contains duplicate stepKey: " + stepKey);
                }
                int stepOrder = requiredInt(step, "stepOrder");
                List<String> dependencies = new ArrayList<>();
                JsonNode dependsOn = step.path("dependsOn");
                if (dependsOn.isArray()) {
                    for (JsonNode dependency : dependsOn) {
                        if (!dependency.isTextual() || dependency.asText().isBlank()) {
                            throw new IllegalArgumentException("Workflow definition contains an invalid dependency for " + stepKey);
                        }
                        dependencies.add(dependency.asText());
                    }
                } else if (!dependsOn.isMissingNode() && !dependsOn.isNull()) {
                    throw new IllegalArgumentException("Workflow definition dependsOn must be an array for " + stepKey);
                }
                plans.add(new WorkflowStepPlan(
                        stepKey,
                        stepOrder,
                        enumValue(AgentType.class, requiredText(step, "agentType"), "agentType"),
                        enumValue(ArtifactType.class, requiredText(step, "artifactType"), "artifactType"),
                        dependencies
                ));
            }
            plans.sort(Comparator.comparingInt(WorkflowStepPlan::stepOrder));
            validateDependencies(plans);
            return List.copyOf(plans);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Workflow definition snapshot is invalid JSON", exception);
        }
    }

    private void validateDependencies(List<WorkflowStepPlan> plans) {
        Map<String, WorkflowStepPlan> byKey = new HashMap<>();
        for (WorkflowStepPlan plan : plans) {
            byKey.put(plan.stepKey(), plan);
        }
        for (WorkflowStepPlan plan : plans) {
            for (String dependency : plan.dependsOn()) {
                if (!byKey.containsKey(dependency)) {
                    throw new IllegalArgumentException("Workflow definition dependency is missing: " + dependency);
                }
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (WorkflowStepPlan plan : plans) {
            detectCycle(plan.stepKey(), byKey, visiting, visited);
        }
    }

    private void detectCycle(
            String stepKey,
            Map<String, WorkflowStepPlan> byKey,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visited.contains(stepKey)) {
            return;
        }
        if (!visiting.add(stepKey)) {
            throw new IllegalArgumentException("Workflow definition contains a dependency cycle at: " + stepKey);
        }
        for (String dependency : byKey.get(stepKey).dependsOn()) {
            detectCycle(dependency, byKey, visiting, visited);
        }
        visiting.remove(stepKey);
        visited.add(stepKey);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Workflow definition requires " + field);
        }
        return value.asText();
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException("Workflow definition requires integer " + field);
        }
        return value.asInt();
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Workflow definition contains unknown " + field + ": " + value);
        }
    }
}
