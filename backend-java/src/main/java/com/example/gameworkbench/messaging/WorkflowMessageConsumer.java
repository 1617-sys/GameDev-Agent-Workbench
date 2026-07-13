package com.example.gameworkbench.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.application.workflow.WorkflowExecutionListener;
import com.example.gameworkbench.application.workflow.WorkflowRunner;
import com.example.gameworkbench.common.enums.WorkflowRunStatus;
import com.example.gameworkbench.config.WorkflowConsumerProperties;
import com.example.gameworkbench.config.WorkflowRetryProperties;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.observability.DiagnosticContext;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.example.gameworkbench.service.RedisService;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/** R3-04 boundary: owns message handling, lock, claim and ACK; the Runner remains transport-agnostic. */
@Slf4j
@Service
@Profile("async")
@RequiredArgsConstructor
public class WorkflowMessageConsumer {

    private static final String LOCK_PREFIX = "workflow:execute:";
    private final WorkflowRunMapper workflowRunMapper;
    private final GameProjectMapper gameProjectMapper;
    private final RedisService redisService;
    private final WorkflowRunner workflowRunner;
    private final WorkflowConsumerProperties consumerProperties;
    private final WorkflowErrorClassifier errorClassifier;
    private final WorkflowRetryProperties retryProperties;
    private final RabbitTemplate rabbitTemplate;
    private final WorkflowRunEventRecorder workflowRunEventRecorder;
    private final ApplicationObservability observability;

    @RabbitListener(queues = "${app.messaging.workflow-queue}", containerFactory = "workflowRabbitListenerContainerFactory")
    public void consume(WorkflowRunMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!valid(message)) {
            ack(channel, deliveryTag);
            return;
        }
        observability.workflowMessage("RECEIVED");
        try (DiagnosticContext ignored = DiagnosticContext.open(
                message.traceId(), message.workflowRunUuid(), message.messageId())) {
        WorkflowRun run = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                .eq(WorkflowRun::getWorkflowRunUuid, message.workflowRunUuid()));
        if (run == null || terminal(run.getStatus()) || run.getAttempt() == null || run.getAttempt() != message.attempt()) {
            observability.workflowMessage("DUPLICATE");
            ack(channel, deliveryTag);
            return;
        }
        if ("PENDING".equals(run.getStatus())) {
            observability.workflowMessage("REDELIVERED");
            nackForRedelivery(channel, deliveryTag);
            return;
        }
        if ("RETRY_WAIT".equals(run.getStatus())) {
            if (workflowRunMapper.queueRetryForDelivery(run.getWorkflowRunUuid(), run.getStatusVersion(), LocalDateTime.now()) != 1) {
                ack(channel, deliveryTag);
                return;
            }
            run.setStatus("QUEUED");
            run.setStatusVersion(run.getStatusVersion() + 1);
            workflowRunEventRecorder.record(run.getWorkflowRunUuid(), "run.status-changed",
                    "retry-delivery." + message.messageId() + ".QUEUED", null, "QUEUED", run.getAttempt(), null, message.traceId());
        }

        String ownerToken = UUID.randomUUID().toString();
        String lockKey = LOCK_PREFIX + run.getWorkflowRunUuid();
        boolean lockAcquired;
        try {
            lockAcquired = redisService.tryLock(lockKey, ownerToken, consumerProperties.executionLockTtlSeconds());
        } catch (RuntimeException exception) {
            log.warn("[WorkflowConsumer] redis lock unavailable traceId={} workflowRunUuid={}", message.traceId(), run.getWorkflowRunUuid());
            nackForRedelivery(channel, deliveryTag);
            return;
        }
        if (!lockAcquired) {
            observability.workflowMessage("DUPLICATE");
            ack(channel, deliveryTag);
            return;
        }

        try {
            if (workflowRunMapper.claimForExecution(run.getWorkflowRunUuid(), message.attempt(), run.getStatusVersion(), LocalDateTime.now()) != 1) {
                observability.workflowMessage("DUPLICATE");
                ack(channel, deliveryTag);
                return;
            }
            if (run.getCreatedAt() != null) {
                observability.workflowQueueLatency(Duration.between(run.getCreatedAt(), LocalDateTime.now()));
            }
            workflowRunEventRecorder.record(run.getWorkflowRunUuid(), "run.status-changed",
                    "consumer." + message.messageId() + ".RUNNING", null, "RUNNING", message.attempt(), null, message.traceId());
            WorkflowRun beforeRunner = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                    .eq(WorkflowRun::getWorkflowRunUuid, run.getWorkflowRunUuid()));
            if (beforeRunner == null || "CANCELED".equals(beforeRunner.getStatus())) {
                ack(channel, deliveryTag);
                return;
            }
            GameProject project = gameProjectMapper.selectById(run.getProjectId());
            if (project == null) {
                routeFailure(run, message, new IllegalArgumentException("Workflow project is unavailable"), channel, deliveryTag);
                return;
            }
            long executionStarted = System.nanoTime();
            try {
                workflowRunner.run(run.getWorkflowRunUuid(), project.getProjectUuid(), WorkflowExecutionListener.noop());
                observability.workflowExecution(Duration.ofNanos(System.nanoTime() - executionStarted), "SUCCESS");
            } catch (RuntimeException exception) {
                observability.workflowExecution(Duration.ofNanos(System.nanoTime() - executionStarted), "FAILED");
                routeFailure(run, message, exception, channel, deliveryTag);
                return;
            }
            WorkflowRun persisted = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                    .eq(WorkflowRun::getWorkflowRunUuid, run.getWorkflowRunUuid()));
            if (persisted != null && terminal(persisted.getStatus())) {
                observability.workflowMessage("ACKED");
                ack(channel, deliveryTag);
            } else {
                nackForRedelivery(channel, deliveryTag);
            }
        } finally {
            try {
                redisService.releaseLock(lockKey, ownerToken);
            } catch (RuntimeException exception) {
                log.warn("[WorkflowConsumer] redis lock release failed traceId={} workflowRunUuid={}", message.traceId(), run.getWorkflowRunUuid());
            }
        }
        }
    }

    private boolean valid(WorkflowRunMessage message) {
        return message != null && message.schemaVersion() == 1 && message.attempt() > 0
                && text(message.messageId()) && text(message.eventId()) && text(message.workflowRunUuid()) && text(message.traceId());
    }

    private boolean terminal(String status) {
        return WorkflowRunStatus.SUCCESS.name().equals(status) || WorkflowRunStatus.FAILED.name().equals(status)
                || WorkflowRunStatus.CANCELED.name().equals(status);
    }

    private boolean text(String value) { return value != null && !value.isBlank(); }
    private void routeFailure(WorkflowRun run, WorkflowRunMessage message, RuntimeException failure, Channel channel, long deliveryTag) throws IOException {
        WorkflowErrorCode code = errorClassifier.classify(failure);
        String error = sanitize(failure.getMessage());
        LocalDateTime now = LocalDateTime.now();
        int nextRetry = (run.getRetryCount() == null ? 0 : run.getRetryCount()) + 1;
        try {
            if (code.retryable() && nextRetry <= retryProperties.maxAttempts()) {
                long delay = retryProperties.delayFor(nextRetry);
                if (workflowRunMapper.recordRetryableFailure(run.getWorkflowRunUuid(), code.name(), error,
                        now.plusNanos(delay * 1_000_000L), now) != 1) { nackForRedelivery(channel, deliveryTag); return; }
                workflowRunEventRecorder.record(run.getWorkflowRunUuid(), "run.status-changed",
                        "consumer." + message.messageId() + ".RETRY_WAIT", null, "RETRY_WAIT", run.getAttempt(), null, message.traceId());
                rabbitTemplate.convertAndSend("workflow.retry", retryKey(nextRetry), message, outbound -> {
                    outbound.getMessageProperties().setHeader("retryCount", nextRetry);
                    outbound.getMessageProperties().setHeader("lastErrorCode", code.name());
                    return outbound;
                });
                observability.workflowRetryOrDlq("RETRY", code.name());
            } else {
                if (workflowRunMapper.recordTerminalFailure(run.getWorkflowRunUuid(), code.name(), error, now) != 1) { nackForRedelivery(channel, deliveryTag); return; }
                workflowRunEventRecorder.record(run.getWorkflowRunUuid(), "run.terminal",
                        "consumer." + message.messageId() + ".FAILED", null, "FAILED", run.getAttempt(), null, message.traceId());
                rabbitTemplate.convertAndSend("workflow.dlx", "workflow.run.failed", message, outbound -> {
                    outbound.getMessageProperties().setHeader("retryCount", run.getRetryCount() == null ? 0 : run.getRetryCount());
                    outbound.getMessageProperties().setHeader("lastErrorCode", code.name());
                    outbound.getMessageProperties().setHeader("lastErrorMessage", error);
                    return outbound;
                });
                observability.workflowRetryOrDlq("DLQ", code.name());
            }
            ack(channel, deliveryTag);
        } catch (RuntimeException handoffFailure) {
            nackForRedelivery(channel, deliveryTag);
        }
    }
    private String retryKey(int retryCount) { return retryCount <= 1 ? "retry.30s" : retryCount == 2 ? "retry.5m" : "retry.30m"; }
    private String sanitize(String error) { return error == null ? "Workflow execution failed" : error.replaceAll("(?i)(authorization|token|password|secret)=?[^\\s,;]+", "$1=[redacted]"); }
    private void ack(Channel channel, long deliveryTag) throws IOException { channel.basicAck(deliveryTag, false); }
    private void nackForRedelivery(Channel channel, long deliveryTag) throws IOException { channel.basicNack(deliveryTag, false, true); }
}
