package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRunCommandServiceImplTest {
    @Mock private WorkflowRunMapper runs;
    @Mock private WorkflowStepRunMapper steps;
    @Mock private OutboxEventMapper outbox;
    @Mock private WorkflowRunEventRecorder events;

    @Test
    void cancelIsPersistedAndIdempotent() {
        WorkflowRun running = run("RUNNING", 1);
        WorkflowRun canceled = run("CANCELED", 1);
        when(runs.selectReadModelByUserIdAndWorkflowRunUuid(7L, "run")).thenReturn(running, canceled);
        when(runs.cancelIfActive(eq(7L), eq("run"), any())).thenReturn(1);

        assertThat(service().cancel(7L, "run").status()).isEqualTo("CANCELED");
        verify(events).record(eq("run"), eq("run.terminal"), any(), eq(null), eq("CANCELED"), eq(1), eq(null), any());
        verify(outbox, never()).insert(any(OutboxEvent.class));
    }

    @Test
    void retryCreatesOneOutboxAndOnlyResetsNonSuccessSteps() {
        WorkflowRun failed = run("FAILED", 1);
        WorkflowRun pendingAttemptTwo = run("PENDING", 2);
        WorkflowStepRun successful = WorkflowStepRun.builder().stepKey("done").status("SUCCESS").attempt(1).build();
        WorkflowStepRun failedStep = WorkflowStepRun.builder().stepKey("retry").status("FAILED").attempt(1).build();
        when(runs.selectReadModelByUserIdAndWorkflowRunUuid(7L, "run")).thenReturn(failed, pendingAttemptTwo);
        when(runs.beginManualRetry(eq(7L), eq("run"), any())).thenReturn(1);
        when(steps.selectByWorkflowRunUuid("run")).thenReturn(List.of(successful, failedStep));

        assertThat(service().retry(7L, "run").attempt()).isEqualTo(2);
        assertThat(failedStep.getStatus()).isEqualTo("PENDING");
        assertThat(failedStep.getAttempt()).isEqualTo(2);
        verify(steps).updateById(failedStep);
        verify(outbox).insert(any(OutboxEvent.class));
        verify(events).record(eq("run"), eq("run.retry-requested"), any(), eq(null), eq("PENDING"), eq(2), eq(null), any());
    }

    @Test
    void retryRejectsNonRetryableAndUnauthorizedRun() {
        when(runs.selectReadModelByUserIdAndWorkflowRunUuid(7L, "run")).thenReturn(run("SUCCESS", 1));
        assertThatThrownBy(() -> service().retry(7L, "run")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service().cancel(null, "run")).isInstanceOf(BusinessException.class);
        verify(outbox, never()).insert(any(OutboxEvent.class));
    }

    private WorkflowRunCommandServiceImpl service() {
        return new WorkflowRunCommandServiceImpl(runs, steps, outbox, events, new ObjectMapper().findAndRegisterModules());
    }
    private WorkflowRun run(String status, int attempt) {
        return WorkflowRun.builder().id(1L).workflowRunUuid("run").status(status).attempt(attempt).build();
    }
}
