package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gameworkbench.config.OutboxPublisherProperties;
import com.example.gameworkbench.config.RabbitMqInfrastructureProperties;
import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.messaging.WorkflowRunMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxEventMapper outboxEvents;
    @Mock private WorkflowRunMapper workflowRuns;
    @Mock private RabbitTemplate rabbitTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxEvents, workflowRuns, rabbitTemplate,
                new RabbitMqInfrastructureProperties("workflow.events", "workflow.run.execute", "workflow.run.requested"),
                new OutboxPublisherProperties(20, 1000, 30000, 5000));
    }

    @Test
    void claimsThenPublishesStableVersionedMessageWithoutMarkingItPublished() {
        OutboxEvent event = event();
        when(outboxEvents.claimForPublish(eq(1L), any(), any(), any())).thenReturn(1);

        publisher.publishIfClaimed(event, LocalDateTime.now());

        ArgumentCaptor<WorkflowRunMessage> message = ArgumentCaptor.forClass(WorkflowRunMessage.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq("workflow.events"), eq("workflow.run.requested"), message.capture(),
                any(MessagePostProcessor.class), correlation.capture());
        assertThat(message.getValue().messageId()).isEqualTo("event-uuid");
        assertThat(message.getValue().eventId()).isEqualTo("event-uuid");
        assertThat(message.getValue().workflowRunUuid()).isEqualTo("run-uuid");
        assertThat(correlation.getValue().getId()).isEqualTo("event-uuid");
        verify(outboxEvents, never()).markPublished(any(), any(), any(), any());
    }

    @Test
    void onlyOnePublisherCanUseAClaimedEvent() {
        when(outboxEvents.claimForPublish(eq(1L), any(), any(), any())).thenReturn(0);

        publisher.publishIfClaimed(event(), LocalDateTime.now());

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void brokerConfirmMarksOnlyClaimedEventPublished() {
        OutboxEvent event = event();
        when(outboxEvents.selectOne(any())).thenReturn(event);
        when(outboxEvents.markPublished(eq(1L), any(), eq("event-uuid"), any())).thenReturn(1);

        publisher.onConfirm(new CorrelationData("event-uuid"), true, null);

        verify(outboxEvents).markPublished(eq(1L), any(), eq("event-uuid"), any());
        verify(workflowRuns).markQueuedAfterOutboxConfirm(eq("run-uuid"), any());
    }

    @Test
    void nackAndExpiredLeaseRemainRecoverable() {
        OutboxEvent event = event();
        when(outboxEvents.selectOne(any())).thenReturn(event);

        publisher.onConfirm(new CorrelationData("event-uuid"), false, "nack");
        publisher.recoverExpiredClaims(LocalDateTime.now());

        verify(outboxEvents).markRetryableFailure(eq(1L), eq("event-uuid"), eq("PUBLISH_NACK"), eq("nack"), any(), any());
        verify(outboxEvents).recoverExpiredPublishingClaims(any(), any());
    }

    @Test
    void scansDueEventsAfterRecoveringStalePublisherLease() {
        OutboxEvent event = event();
        when(outboxEvents.selectDueForPublish(any(), eq(20))).thenReturn(List.of(event));
        when(outboxEvents.claimForPublish(eq(1L), any(), any(), any())).thenReturn(0);

        publisher.publishDueEvents();

        verify(outboxEvents).recoverExpiredPublishingClaims(any(), any());
        verify(outboxEvents).selectDueForPublish(any(), eq(20));
    }

    private OutboxEvent event() {
        return OutboxEvent.builder().id(1L).eventUuid("event-uuid").workflowRunUuid("run-uuid")
                .traceId("trace-id").publishAttempt(0).createdAt(LocalDateTime.now()).build();
    }
}
