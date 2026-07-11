package com.example.gameworkbench.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gameworkbench.config.WorkflowRecoveryProperties;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.entity.WorkflowRecoveryAuditEvent;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.mapper.WorkflowRecoveryAuditEventMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowRecoveryServiceTest {
    @Mock private WorkflowRunMapper runs;
    @Mock private OutboxEventMapper outbox;
    @Mock private WorkflowRecoveryAuditEventMapper audits;
    private final WorkflowRecoveryProperties properties = new WorkflowRecoveryProperties(true, 10, 1_000, 1_000, 1_000, 3);

    @Test
    void stalePendingIsVersionClaimedThenReissuedThroughOutboxAndAudited() {
        WorkflowRecoveryService service = service();
        WorkflowRun run = run("PENDING", 0);
        when(runs.selectStalePending(any(), eq(10))).thenReturn(List.of(run));
        when(runs.selectStaleQueued(any(), eq(10))).thenReturn(List.of());
        when(runs.selectStaleRunning(any(), eq(10))).thenReturn(List.of());
        when(runs.claimForRecovery(eq("run"), eq("PENDING"), eq(4L), any(), any(), eq("PENDING"))).thenReturn(1);

        service.scan(LocalDateTime.now());

        verify(outbox).insert(any(OutboxEvent.class));
        verify(audits).insert(any(WorkflowRecoveryAuditEvent.class));
    }

    @Test
    void staleRunningUsesRetryWaitInsteadOfBlindQueuedReset() {
        WorkflowRecoveryService service = service();
        WorkflowRun run = run("RUNNING", 1);
        when(runs.claimForRecovery(eq("run"), eq("RUNNING"), eq(4L), any(), any(), eq("RETRY_WAIT"))).thenReturn(1);

        service.recoverOne(run, LocalDateTime.now(), "RUNNING_HEARTBEAT_STALE");

        verify(outbox).insert(any(OutboxEvent.class));
        verify(audits).insert(any(WorkflowRecoveryAuditEvent.class));
    }

    @Test
    void staleRunClaimedByAnotherScannerCreatesNoOutboxOrAudit() {
        WorkflowRecoveryService service = service();
        WorkflowRun run = run("QUEUED", 0);
        when(runs.claimForRecovery(eq("run"), eq("QUEUED"), eq(4L), any(), any(), eq("QUEUED"))).thenReturn(0);

        service.recoverOne(run, LocalDateTime.now(), "QUEUED_DELIVERY_OVERDUE");

        verify(outbox, never()).insert(any(OutboxEvent.class));
        verify(audits, never()).insert(any(WorkflowRecoveryAuditEvent.class));
    }

    private WorkflowRecoveryService service() {
        return new WorkflowRecoveryService(runs, outbox, audits, properties, new ObjectMapper().findAndRegisterModules());
    }

    private WorkflowRun run(String status, int recoveryAttempt) {
        return WorkflowRun.builder().id(1L).workflowRunUuid("run").attempt(1).status(status)
                .statusVersion(4L).recoveryAttempt(recoveryAttempt).build();
    }
}
