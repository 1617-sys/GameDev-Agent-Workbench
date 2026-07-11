package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.config.WorkflowRecoveryProperties;
import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.entity.WorkflowRecoveryAuditEvent;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.mapper.WorkflowRecoveryAuditEventMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;
import com.example.gameworkbench.messaging.WorkflowRunMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recreates durable delivery intent only after a versioned database claim. */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(WorkflowRecoveryProperties.class)
public class WorkflowRecoveryService {
    private final WorkflowRunMapper workflowRuns;
    private final OutboxEventMapper outboxEvents;
    private final WorkflowRecoveryAuditEventMapper auditEvents;
    private final WorkflowRecoveryProperties properties;
    private final ObjectMapper objectMapper;
    private final WorkflowRunEventRecorder workflowRunEventRecorder;

    @Scheduled(fixedDelayString = "${app.workflow-recovery.scan-delay-ms:30000}")
    public void scanScheduled() {
        if (properties.enabled()) scan(LocalDateTime.now());
    }

    public void scan(LocalDateTime now) {
        recover(workflowRuns.selectStalePending(now.minusNanos(properties.pendingStaleMs() * 1_000_000L), properties.batchSize()), now, "PENDING_OUTBOX_STALE");
        recover(workflowRuns.selectStaleQueued(now.minusNanos(properties.queuedStaleMs() * 1_000_000L), properties.batchSize()), now, "QUEUED_DELIVERY_OVERDUE");
        recover(workflowRuns.selectStaleRunning(now.minusNanos(properties.runningHeartbeatStaleMs() * 1_000_000L), properties.batchSize()), now, "RUNNING_HEARTBEAT_STALE");
    }

    private void recover(List<WorkflowRun> runs, LocalDateTime now, String reason) {
        for (WorkflowRun run : runs) {
            try { recoverOne(run, now, reason); }
            catch (RuntimeException exception) {
                log.warn("[WorkflowRecovery] failed workflowRunUuid={} reason={}", run.getWorkflowRunUuid(), reason, exception);
            }
        }
    }

    @Transactional
    void recoverOne(WorkflowRun run, LocalDateTime now, String reason) {
        int currentAttempts = run.getRecoveryAttempt() == null ? 0 : run.getRecoveryAttempt();
        boolean exhausted = currentAttempts >= properties.maxRecoveryAttempts();
        String nextStatus = exhausted ? "FAILED" : "RUNNING".equals(run.getStatus()) ? "RETRY_WAIT" : run.getStatus();
        LocalDateTime staleBefore = staleBefore(now, run.getStatus());
        int claimed = workflowRuns.claimForRecovery(run.getWorkflowRunUuid(), run.getStatus(), run.getStatusVersion(), staleBefore, now, nextStatus);
        if (claimed != 1) return;
        String traceId = UUID.randomUUID().toString();
        if (exhausted) {
            auditEvents.insert(WorkflowRecoveryAuditEvent.builder().workflowRunUuid(run.getWorkflowRunUuid())
                    .previousStatus(run.getStatus()).newStatus(nextStatus).reason(reason + "_MAX_ATTEMPTS")
                    .recoveryAttempt(currentAttempts + 1).traceId(traceId).createdAt(now).build());
            workflowRunEventRecorder.record(run.getWorkflowRunUuid(), "run.recovered",
                    "recovery." + currentAttempts + ".FAILED", null, nextStatus, run.getAttempt(), null, traceId);
            return;
        }
        String eventId = UUID.randomUUID().toString();
        WorkflowRunMessage message = new WorkflowRunMessage(1, UUID.randomUUID().toString(), eventId,
                run.getWorkflowRunUuid(), run.getAttempt(), traceId, now);
        outboxEvents.insert(OutboxEvent.builder().eventUuid(eventId).aggregateType("WORKFLOW_RUN")
                .aggregateUuid(run.getWorkflowRunUuid()).workflowRunId(run.getId()).workflowRunUuid(run.getWorkflowRunUuid())
                .eventType("WORKFLOW_RUN_RECOVERY_REQUESTED").payloadJson(payload(message))
                .schemaVersion("workflow-run-requested/1").status("PENDING").publishAttempt(0).nextAttemptAt(now)
                .traceId(traceId).createdAt(now).updatedAt(now).build());
        auditEvents.insert(WorkflowRecoveryAuditEvent.builder().workflowRunUuid(run.getWorkflowRunUuid())
                .previousStatus(run.getStatus()).newStatus(nextStatus).reason(reason).recoveryAttempt(currentAttempts + 1)
                .eventId(eventId).traceId(traceId).createdAt(now).build());
        workflowRunEventRecorder.record(run.getWorkflowRunUuid(), "run.recovered",
                "recovery." + currentAttempts + "." + nextStatus, null, nextStatus, run.getAttempt(), null, traceId);
        log.info("[WorkflowRecovery] recovered workflowRunUuid={} previousStatus={} newStatus={} recoveryAttempt={} eventId={} traceId={}",
                run.getWorkflowRunUuid(), run.getStatus(), nextStatus, currentAttempts + 1, eventId, traceId);
    }

    private String payload(WorkflowRunMessage message) {
        try { return objectMapper.writeValueAsString(message); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize recovery outbox event", exception); }
    }

    private LocalDateTime staleBefore(LocalDateTime now, String status) {
        long staleMs = "RUNNING".equals(status) ? properties.runningHeartbeatStaleMs()
                : "QUEUED".equals(status) ? properties.queuedStaleMs() : properties.pendingStaleMs();
        return now.minusNanos(staleMs * 1_000_000L);
    }
}
