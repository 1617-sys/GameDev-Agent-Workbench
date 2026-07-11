package com.example.gameworkbench.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.common.enums.WorkflowRunStatus;
import com.example.gameworkbench.common.enums.WorkflowStepRunStatus;

class WorkflowStatusPolicyTest {

    @Test
    void shouldAllowLegalWorkflowRunTransitions() {
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.PENDING, WorkflowRunStatus.QUEUED)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.PENDING, WorkflowRunStatus.RUNNING)).isFalse();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.QUEUED, WorkflowRunStatus.RUNNING)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.SUCCESS)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.FAILED)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.RETRY_WAIT)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.RETRY_WAIT, WorkflowRunStatus.QUEUED)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.TIMEOUT)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.CANCELED)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.FAILED, WorkflowRunStatus.QUEUED)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(WorkflowRunStatus.TIMEOUT, WorkflowRunStatus.QUEUED)).isTrue();
    }

    @Test
    void shouldRejectTerminalWorkflowRunTransitionsWithClearMessage() {
        assertThatIllegalStateException()
                .isThrownBy(() -> WorkflowStatusPolicy.requireTransition(
                        WorkflowRunStatus.SUCCESS, WorkflowRunStatus.RUNNING))
                .withMessage("Cannot transition workflow run from SUCCESS to RUNNING");
        assertThatIllegalStateException()
                .isThrownBy(() -> WorkflowStatusPolicy.requireTransition(
                        WorkflowRunStatus.FAILED, WorkflowRunStatus.RUNNING))
                .withMessage("Cannot transition workflow run from FAILED to RUNNING");
        assertThatIllegalStateException()
                .isThrownBy(() -> WorkflowStatusPolicy.requireTransition(
                        WorkflowRunStatus.TIMEOUT, WorkflowRunStatus.RUNNING))
                .withMessage("Cannot transition workflow run from TIMEOUT to RUNNING");
        assertThatIllegalStateException()
                .isThrownBy(() -> WorkflowStatusPolicy.requireTransition(
                        WorkflowRunStatus.CANCELED, WorkflowRunStatus.RUNNING))
                .withMessage("Cannot transition workflow run from CANCELED to RUNNING");
    }

    @Test
    void shouldAllowStepRunToStartOnlyAfterDependenciesSucceed() {
        assertThat(WorkflowStatusPolicy.canTransition(
                WorkflowStepRunStatus.PENDING, WorkflowStepRunStatus.RUNNING, true)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(
                WorkflowStepRunStatus.PENDING, WorkflowStepRunStatus.RUNNING, false)).isFalse();

        assertThatIllegalStateException()
                .isThrownBy(() -> WorkflowStatusPolicy.requireTransition(
                        WorkflowStepRunStatus.PENDING, WorkflowStepRunStatus.RUNNING, false))
                .withMessage("Cannot transition workflow step run from PENDING to RUNNING before all dependencies succeed");
    }

    @Test
    void shouldAllowLegalStepRunTransitionsAndRejectIrreversibleTerminalStates() {
        assertThat(WorkflowStatusPolicy.canTransition(
                WorkflowStepRunStatus.RUNNING, WorkflowStepRunStatus.SUCCESS, true)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(
                WorkflowStepRunStatus.RUNNING, WorkflowStepRunStatus.FAILED, true)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(
                WorkflowStepRunStatus.RUNNING, WorkflowStepRunStatus.TIMEOUT, true)).isTrue();
        assertThat(WorkflowStatusPolicy.canTransition(
                WorkflowStepRunStatus.FAILED, WorkflowStepRunStatus.PENDING, true)).isTrue();

        assertThatIllegalStateException()
                .isThrownBy(() -> WorkflowStatusPolicy.requireTransition(
                        WorkflowStepRunStatus.SUCCESS, WorkflowStepRunStatus.RUNNING, true))
                .withMessage("Cannot transition workflow step run from SUCCESS to RUNNING");
        assertThatIllegalStateException()
                .isThrownBy(() -> WorkflowStatusPolicy.requireTransition(
                        WorkflowStepRunStatus.CANCELED, WorkflowStepRunStatus.RUNNING, true))
                .withMessage("Cannot transition workflow step run from CANCELED to RUNNING");
    }
}
