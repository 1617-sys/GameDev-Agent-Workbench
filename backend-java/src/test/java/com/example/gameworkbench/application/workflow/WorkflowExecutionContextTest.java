package com.example.gameworkbench.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.entity.WorkflowRun;
import com.fasterxml.jackson.databind.ObjectMapper;

class WorkflowExecutionContextTest {

    private final WorkflowStepPlanParser parser = new WorkflowStepPlanParser(new ObjectMapper());

    @Test
    void shouldParseStablePlansAndRequireCompletedDependencies() {
        List<WorkflowStepPlan> plans = parser.parse("""
                {"steps":[
                  {"stepKey":"second","stepOrder":2,"agentType":"CORE_LOOP_DESIGN","artifactType":"CORE_LOOP_DESIGN_RESULT","dependsOn":["first"]},
                  {"stepKey":"first","stepOrder":1,"agentType":"GAME_CONCEPT","artifactType":"GAME_CONCEPT_RESULT","dependsOn":[]}
                ]}
                """);
        WorkflowExecutionContext context = new WorkflowExecutionContext(new WorkflowRun(), "project", "input", plans);

        assertThat(plans).extracting(WorkflowStepPlan::stepKey).containsExactly("first", "second");
        assertThat(context.dependenciesSatisfied(plans.get(1))).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> context.dependencyOutputs(plans.get(1)));

        StepOutput output = new StepOutput("concept", "artifact-1", null, null);
        context.recordCompletedOutput("first", output);
        assertThat(context.dependenciesSatisfied(plans.get(1))).isTrue();
        assertThat(context.dependencyOutputs(plans.get(1))).containsExactly(output);
        assertThatIllegalStateException().isThrownBy(() -> context.recordCompletedOutput("first", output));
    }

    @Test
    void shouldRejectInvalidPlansBeforeExecution() {
        assertThatIllegalArgumentException().isThrownBy(() -> parser.parse("not-json"));
        assertThatIllegalArgumentException().isThrownBy(() -> parser.parse("""
                {"steps":[
                  {"stepKey":"a","stepOrder":1,"agentType":"GAME_CONCEPT","artifactType":"GAME_CONCEPT_RESULT","dependsOn":["b"]},
                  {"stepKey":"b","stepOrder":2,"agentType":"CORE_LOOP_DESIGN","artifactType":"CORE_LOOP_DESIGN_RESULT","dependsOn":["a"]}
                ]}
                """));
        assertThatIllegalArgumentException().isThrownBy(() -> parser.parse("""
                {"steps":[{"stepKey":"a","stepOrder":1,"agentType":"UNKNOWN","artifactType":"GAME_CONCEPT_RESULT","dependsOn":[]}]}
                """));
    }
}
