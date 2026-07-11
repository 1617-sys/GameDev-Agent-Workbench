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
import com.example.gameworkbench.service.RedisService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.LocalDateTime;
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

    @RabbitListener(queues = "${app.messaging.workflow-queue}", containerFactory = "workflowRabbitListenerContainerFactory")
    public void consume(WorkflowRunMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!valid(message)) {
            ack(channel, deliveryTag);
            return;
        }
        WorkflowRun run = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                .eq(WorkflowRun::getWorkflowRunUuid, message.workflowRunUuid()));
        if (run == null || terminal(run.getStatus()) || run.getAttempt() == null || run.getAttempt() != message.attempt()) {
            ack(channel, deliveryTag);
            return;
        }
        if ("PENDING".equals(run.getStatus())) {
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
            ack(channel, deliveryTag);
            return;
        }

        try {
            if (workflowRunMapper.claimForExecution(run.getWorkflowRunUuid(), message.attempt(), run.getStatusVersion(), LocalDateTime.now()) != 1) {
                ack(channel, deliveryTag);
                return;
            }
            GameProject project = gameProjectMapper.selectById(run.getProjectId());
            if (project == null) {
                routeFailure(run, message, new IllegalArgumentException("Workflow project is unavailable"), channel, deliveryTag);
                return;
            }
            try {
                workflowRunner.run(run.getWorkflowRunUuid(), project.getProjectUuid(), WorkflowExecutionListener.noop());
            } catch (RuntimeException exception) {
                routeFailure(run, message, exception, channel, deliveryTag);
                return;
            }
            WorkflowRun persisted = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                    .eq(WorkflowRun::getWorkflowRunUuid, run.getWorkflowRunUuid()));
            if (persisted != null && terminal(persisted.getStatus())) {
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
                rabbitTemplate.convertAndSend("workflow.retry", retryKey(nextRetry), message, outbound -> {
                    outbound.getMessageProperties().setHeader("retryCount", nextRetry);
                    outbound.getMessageProperties().setHeader("lastErrorCode", code.name());
                    return outbound;
                });
            } else {
                if (workflowRunMapper.recordTerminalFailure(run.getWorkflowRunUuid(), code.name(), error, now) != 1) { nackForRedelivery(channel, deliveryTag); return; }
                rabbitTemplate.convertAndSend("workflow.dlx", "workflow.run.failed", message, outbound -> {
                    outbound.getMessageProperties().setHeader("retryCount", run.getRetryCount() == null ? 0 : run.getRetryCount());
                    outbound.getMessageProperties().setHeader("lastErrorCode", code.name());
                    outbound.getMessageProperties().setHeader("lastErrorMessage", error);
                    return outbound;
                });
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
