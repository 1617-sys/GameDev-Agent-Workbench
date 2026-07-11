package com.example.gameworkbench.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;

class AgentStepExecutorTest {

    @Test
    void shouldExecuteOneStepAfterDependenciesAreAvailable() {
        AgentRunService agentRunService = Mockito.mock(AgentRunService.class);
        when(agentRunService.run(any(), any())).thenReturn(AgentRunVO.builder().id(9L).outputContent("result").build());
        WorkflowStepPlan plan = new WorkflowStepPlan("next", 2, AgentType.CORE_LOOP_DESIGN,
                ArtifactType.CORE_LOOP_DESIGN_RESULT, List.of("first"));
        WorkflowRun run = new WorkflowRun();
        run.setUserId(1L);
        run.setWorkflowType("GAME_DESIGN");
        WorkflowExecutionContext context = new WorkflowExecutionContext(run, "project", "input", List.of(plan));
        context.recordCompletedOutput("first", new StepOutput("previous", "artifact", null, null));

        StepExecutionResult result = new AgentStepExecutor(agentRunService).execute(context, plan);

        assertThat(result.output().content()).isEqualTo("result");
        assertThat(result.agentRunId()).isEqualTo(9L);
        verify(agentRunService).run(any(), any());
    }

    @Test
    void shouldRejectAgentCallWhenDependencyOutputIsMissing() {
        AgentRunService agentRunService = Mockito.mock(AgentRunService.class);
        WorkflowStepPlan plan = new WorkflowStepPlan("next", 2, AgentType.CORE_LOOP_DESIGN,
                ArtifactType.CORE_LOOP_DESIGN_RESULT, List.of("first"));
        WorkflowExecutionContext context = new WorkflowExecutionContext(new WorkflowRun(), "project", "input", List.of(plan));

        assertThatIllegalStateException().isThrownBy(() -> new AgentStepExecutor(agentRunService).execute(context, plan));
    }
}
