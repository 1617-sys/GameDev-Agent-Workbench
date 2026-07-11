package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.gameworkbench.config.OutboxPublisherProperties;
import com.example.gameworkbench.config.RabbitMqInfrastructureProperties;
import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.messaging.WorkflowRunMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Publishes only committed Outbox events. Broker confirmation is never treated as workflow completion. */
@Slf4j
@Service
@Profile("async")
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqInfrastructureProperties messagingProperties;
    private final OutboxPublisherProperties publisherProperties;
    private final String publisherOwner = UUID.randomUUID().toString();

    @PostConstruct
    void configureCallbacks() {
        rabbitTemplate.setConfirmCallback(this::onConfirm);
        rabbitTemplate.setReturnsCallback(returned -> {
            String eventId = returned.getMessage().getMessageProperties().getCorrelationId();
            if (eventId != null) {
                markRetry(eventId, "PUBLISH_RETURNED", "Broker returned an unroutable message");
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.poll-delay-ms:1000}")
    public void publishDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        recoverExpiredClaims(now);
        List<OutboxEvent> dueEvents = outboxEventMapper.selectDueForPublish(now, publisherProperties.batchSize());
        for (OutboxEvent event : dueEvents) {
            publishIfClaimed(event, now);
        }
    }

    void publishIfClaimed(OutboxEvent event, LocalDateTime now) {
        LocalDateTime claimUntil = now.plusNanos(publisherProperties.claimLeaseMs() * 1_000_000L);
        if (outboxEventMapper.claimForPublish(event.getId(), publisherOwner, claimUntil, now) != 1) {
            return;
        }
        event.setPublishAttempt(event.getPublishAttempt() + 1);
        String messageId = event.getEventUuid();
        WorkflowRunMessage message = new WorkflowRunMessage(1, messageId, event.getEventUuid(), event.getWorkflowRunUuid(),
                event.getPublishAttempt(), event.getTraceId(), event.getCreatedAt());
        try {
            rabbitTemplate.convertAndSend(messagingProperties.workflowExchange(), messagingProperties.workflowRoutingKey(), message,
                    outbound -> withTraceHeaders(outbound, event, messageId), new CorrelationData(event.getEventUuid()));
            log.info("[OutboxPublisher] published awaiting-confirm traceId={} workflowRunUuid={} outboxEventId={} messageId={}",
                    event.getTraceId(), event.getWorkflowRunUuid(), event.getId(), messageId);
        } catch (RuntimeException exception) {
            markRetry(event.getEventUuid(), "PUBLISH_EXCEPTION", exception.getClass().getSimpleName());
        }
    }

    private Message withTraceHeaders(Message message, OutboxEvent event, String messageId) {
        message.getMessageProperties().setMessageId(messageId);
        message.getMessageProperties().setCorrelationId(event.getEventUuid());
        message.getMessageProperties().setHeader("eventId", event.getEventUuid());
        message.getMessageProperties().setHeader("workflowRunUuid", event.getWorkflowRunUuid());
        message.getMessageProperties().setHeader("traceId", event.getTraceId());
        message.getMessageProperties().setHeader("schemaVersion", 1);
        return message;
    }

    void onConfirm(CorrelationData correlationData, boolean ack, String cause) {
        if (correlationData == null || correlationData.getId() == null) {
            return;
        }
        String eventId = correlationData.getId();
        if (ack) {
            OutboxEvent event = outboxEventMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OutboxEvent>()
                    .eq(OutboxEvent::getEventUuid, eventId));
            if (event != null && outboxEventMapper.markPublished(event.getId(), publisherOwner, eventId, LocalDateTime.now()) == 1) {
                log.info("[OutboxPublisher] confirmed traceId={} workflowRunUuid={} outboxEventId={} messageId={}",
                        event.getTraceId(), event.getWorkflowRunUuid(), event.getId(), eventId);
            }
        } else {
            markRetry(eventId, "PUBLISH_NACK", cause == null ? "Broker negatively acknowledged publish" : cause);
        }
    }

    void recoverExpiredClaims(LocalDateTime now) {
        outboxEventMapper.recoverExpiredPublishingClaims(now, now.plusNanos(publisherProperties.retryDelayMs() * 1_000_000L));
    }

    void markRetry(String eventId, String errorCode, String errorMessage) {
        OutboxEvent event = outboxEventMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventUuid, eventId));
        if (event == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        outboxEventMapper.markRetryableFailure(event.getId(), eventId, errorCode, sanitize(errorMessage),
                now.plusNanos(publisherProperties.retryDelayMs() * 1_000_000L), now);
        log.warn("[OutboxPublisher] publish retry scheduled traceId={} workflowRunUuid={} outboxEventId={} messageId={} reason={}",
                event.getTraceId(), event.getWorkflowRunUuid(), event.getId(), eventId, errorCode);
    }

    private String sanitize(String message) {
        return message == null ? "publish failure" : message.replaceAll("(?i)(authorization|token|password|secret)=?[^\\s,;]+", "$1=[redacted]");
    }
}
