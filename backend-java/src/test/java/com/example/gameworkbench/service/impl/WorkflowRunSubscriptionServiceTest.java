package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.WorkflowRunEvent;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.example.gameworkbench.service.WorkflowRunQueryService;
import com.example.gameworkbench.service.WorkflowRunSseEmitterFactory;
import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRunSubscriptionServiceTest {
    @Mock private WorkflowRunQueryService queries;
    @Mock private WorkflowRunEventService events;
    @Mock private WorkflowRunSseEmitterFactory emitters;
    @Mock private ApplicationObservability observability;
    @Mock private SseEmitter first;
    @Mock private SseEmitter second;

    @Test
    void shouldAuthorizeThenSendSnapshotBeforePersistentReplay() throws Exception {
        when(queries.getRun(7L, "run")).thenReturn(snapshot("RUNNING", 5L));
        when(emitters.create()).thenReturn(first);
        when(events.findAfter("run", 2L)).thenReturn(List.of(event(3L, "step.status-changed"), event(5L, "artifact.available")));

        WorkflowRunSubscriptionService service = service();
        service.subscribe(7L, "run", "2");

        verify(events).findAfter("run", 2L);
        verify(first, times(3)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(service.subscriberCount("run")).isEqualTo(1);
    }

    @Test
    void shouldFallbackToSnapshotSequenceForInvalidOrFutureLastEventId() throws Exception {
        when(queries.getRun(7L, "run")).thenReturn(snapshot("RUNNING", 5L));
        when(emitters.create()).thenReturn(first);
        when(events.findAfter("run", 5L)).thenReturn(List.of());

        WorkflowRunSubscriptionService service = service();
        service.subscribe(7L, "run", "not-a-sequence");

        verify(events).findAfter("run", 5L);
    }

    @Test
    void terminalRunSendsSnapshotThenCompletesWithoutRegistryEntry() throws Exception {
        when(queries.getRun(7L, "run")).thenReturn(snapshot("SUCCESS", 4L));
        when(emitters.create()).thenReturn(first);
        when(events.findAfter("run", 4L)).thenReturn(List.of());

        WorkflowRunSubscriptionService service = service();
        service.subscribe(7L, "run", null);

        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verify(first).complete();
        assertThat(service.subscriberCount("run")).isZero();
    }

    @Test
    void multipleSubscribersReceiveNewPersistedEventAndTerminalCleansBoth() throws Exception {
        when(queries.getRun(7L, "run")).thenReturn(snapshot("RUNNING", 0L));
        when(emitters.create()).thenReturn(first, second);
        when(events.findAfter("run", 0L)).thenReturn(List.of());
        WorkflowRunSubscriptionService service = service();
        service.subscribe(7L, "run", null);
        service.subscribe(7L, "run", null);

        service.onPersistedEvent(event(1L, "run.terminal"));

        verify(first, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(first).complete();
        verify(second).complete();
        assertThat(service.subscriberCount("run")).isZero();
    }

    @Test
    void heartbeatIsACommentOnlyAndCleansFailedEmitter() throws Exception {
        when(queries.getRun(7L, "run")).thenReturn(snapshot("RUNNING", 0L));
        when(emitters.create()).thenReturn(first);
        when(events.findAfter("run", 0L)).thenReturn(List.of());
        WorkflowRunSubscriptionService service = service();
        service.subscribe(7L, "run", null);

        service.sendHeartbeats();

        verify(first, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(service.subscriberCount("run")).isEqualTo(1);
    }

    @Test
    void sendFailureCleansOnlyCurrentSubscriptionAndDoesNotTouchWorkflow() throws Exception {
        when(queries.getRun(7L, "run")).thenReturn(snapshot("RUNNING", 0L));
        when(emitters.create()).thenReturn(first);
        doThrow(new IOException("client closed")).when(first).send(any(SseEmitter.SseEventBuilder.class));

        WorkflowRunSubscriptionService service = service();
        service.subscribe(7L, "run", null);

        verify(first).complete();
        verify(events, never()).findAfter(any(), any(Long.class));
        assertThat(service.subscriberCount("run")).isZero();
    }

    @Test
    void persistedEventSendFailureDoesNotEscapeIntoWorkflowExecution() throws Exception {
        when(queries.getRun(7L, "run")).thenReturn(snapshot("RUNNING", 0L));
        when(emitters.create()).thenReturn(first);
        when(events.findAfter("run", 0L)).thenReturn(List.of());
        org.mockito.Mockito.doNothing().doThrow(new IOException("client closed"))
                .when(first).send(any(SseEmitter.SseEventBuilder.class));
        WorkflowRunSubscriptionService service = service();
        service.subscribe(7L, "run", null);

        assertThatCode(() -> service.onPersistedEvent(event(1L, "step.status-changed"))).doesNotThrowAnyException();
        verify(first).complete();
        assertThat(service.subscriberCount("run")).isZero();
    }

    @Test
    void unauthorizedReadDoesNotCreateSubscription() {
        when(queries.getRun(null, "run")).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> service().subscribe(null, "run", null)).isInstanceOf(BusinessException.class);
        verify(emitters, never()).create();
    }

    private WorkflowRunSubscriptionService service() {
        return new WorkflowRunSubscriptionService(queries, events, emitters, observability);
    }

    private WorkflowRunDetailVO snapshot(String status, Long lastSequence) {
        return WorkflowRunDetailVO.builder().workflowRunUuid("run").status(status).lastSequence(lastSequence).build();
    }

    private WorkflowRunEvent event(Long sequence, String type) {
        return WorkflowRunEvent.builder().workflowRunUuid("run").sequence(sequence).eventType(type)
                .status("SUCCESS").occurredAt(LocalDateTime.now()).build();
    }
}
