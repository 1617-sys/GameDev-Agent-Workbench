package com.example.gameworkbench.application.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.gameworkbench.entity.WorkflowRun;

public class WorkflowExecutionContext {

    private final WorkflowRun workflowRun;
    private final String projectUuid;
    private final String inputSnapshot;
    private final List<WorkflowStepPlan> plans;
    private final Map<String, StepOutput> completedOutputs = new LinkedHashMap<>();

    public WorkflowExecutionContext(
            WorkflowRun workflowRun,
            String projectUuid,
            String inputSnapshot,
            List<WorkflowStepPlan> plans
    ) {
        this.workflowRun = workflowRun;
        this.projectUuid = projectUuid;
        this.inputSnapshot = inputSnapshot;
        this.plans = List.copyOf(plans);
    }

    public WorkflowRun workflowRun() { return workflowRun; }
    public String projectUuid() { return projectUuid; }
    public String inputSnapshot() { return inputSnapshot; }
    public List<WorkflowStepPlan> plans() { return plans; }

    public void recordCompletedOutput(String stepKey, StepOutput output) {
        if (completedOutputs.containsKey(stepKey)) {
            throw new IllegalStateException("Completed output already exists for step: " + stepKey);
        }
        completedOutputs.put(stepKey, output);
    }

    public StepOutput completedOutput(String stepKey) {
        StepOutput output = completedOutputs.get(stepKey);
        if (output == null) {
            throw new IllegalStateException("Completed output is missing for step: " + stepKey);
        }
        return output;
    }

    public List<StepOutput> dependencyOutputs(WorkflowStepPlan plan) {
        return plan.dependsOn().stream().map(this::completedOutput).toList();
    }

    public boolean dependenciesSatisfied(WorkflowStepPlan plan) {
        return plan.dependsOn().stream().allMatch(completedOutputs::containsKey);
    }
}
