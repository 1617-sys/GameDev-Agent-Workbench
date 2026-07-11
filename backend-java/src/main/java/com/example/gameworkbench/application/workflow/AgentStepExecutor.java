package com.example.gameworkbench.application.workflow;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;

@Component
public class AgentStepExecutor implements WorkflowStepExecutor {

    private final AgentRunService agentRunService;

    public AgentStepExecutor(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @Override
    public boolean supports(WorkflowStepPlan stepPlan) {
        return stepPlan.agentType() != null;
    }

    @Override
    public StepExecutionResult execute(WorkflowExecutionContext context, WorkflowStepPlan stepPlan) {
        if (!context.dependenciesSatisfied(stepPlan)) {
            throw new IllegalStateException("Workflow step dependencies are not satisfied: " + stepPlan.stepKey());
        }
        String dependencyContext = context.dependencyOutputs(stepPlan).stream()
                .map(StepOutput::content)
                .collect(Collectors.joining("\n\n"));
        AgentRunVO run = agentRunService.run(context.workflowRun().getUserId(), AgentRunRequest.builder()
                .projectUuid(context.projectUuid())
                .agentType(stepPlan.agentType())
                .title(context.workflowRun().getWorkflowType())
                .content(context.inputSnapshot())
                .context(dependencyContext)
                .build());
        return new StepExecutionResult(new StepOutput(
                run.getOutputContent(), null, null, null), run.getId());
    }
}
