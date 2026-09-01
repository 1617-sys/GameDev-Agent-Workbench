package com.example.gameworkbench.application.workflow;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;

/**
 * 把一个“由 AI Agent 执行”的工作流步骤适配到统一的步骤执行接口。
 *
 * <p>工作流编排器只认识 {@link WorkflowStepExecutor}；本类负责把工作流上下文转换成
 * {@link AgentRunRequest}，真正的模型选择、调用和 Agent 运行记录由 {@link AgentRunService} 负责。</p>
 */
@Component
public class AgentStepExecutor implements WorkflowStepExecutor {

    private final AgentRunService agentRunService;

    public AgentStepExecutor(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @Override
    public boolean supports(WorkflowStepPlan stepPlan) {
        // 约定：配置了 agentType 的步骤交给本执行器；其他步骤可由别的执行器扩展。
        return stepPlan.agentType() != null;
    }

    @Override
    public StepExecutionResult execute(WorkflowExecutionContext context, WorkflowStepPlan stepPlan) {
        if (!context.dependenciesSatisfied(stepPlan)) {
            throw new IllegalStateException("Workflow step dependencies are not satisfied: " + stepPlan.stepKey());
        }
        // 将所有前置步骤的文本输出拼成模型上下文，使当前 Agent 能消费上游产物。
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
        // 同时返回 AgentRun 主键，便于工作流步骤和底层模型调用记录建立可追踪关系。
        return new StepExecutionResult(new StepOutput(
                run.getOutputContent(), null, null, null), run.getId());
    }
}
