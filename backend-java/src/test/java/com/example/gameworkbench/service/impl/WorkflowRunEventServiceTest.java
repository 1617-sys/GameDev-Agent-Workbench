package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowRunEvent;
import com.example.gameworkbench.mapper.WorkflowRunEventMapper;
import com.example.gameworkbench.service.WorkflowRunEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRunEventServiceTest {
    @Mock private WorkflowRunEventMapper events;
    @Mock private WorkflowRunEventPublisher publisher;

    @Test
    void shouldLockAllocatePersistAndPublishOnlySafeEvent() {
        when(events.lockRunForEventAppend("run")).thenReturn(WorkflowRun.builder().workflowRunUuid("run").build());
        when(events.selectByRunAndEventKey("run", "step.design.1.SUCCESS")).thenReturn(null);
        when(events.allocateNextSequence("run")).thenReturn(1);
        when(events.selectCurrentSequence("run")).thenReturn(4L);

        WorkflowRunEvent event = service().record("run", "step.status-changed", "step.design.1.SUCCESS", "design",
                "SUCCESS", 1, null, "trace");

        assertThat(event.getSequence()).isEqualTo(4L);
        assertThat(event.getPayloadJson()).contains("workflowRunUuid", "stepKey", "SUCCESS")
                .doesNotContain("secret", "prompt", "authorization");
        InOrder order = inOrder(events);
        order.verify(events).lockRunForEventAppend("run");
        order.verify(events).selectByRunAndEventKey("run", "step.design.1.SUCCESS");
        order.verify(events).allocateNextSequence("run");
        order.verify(events).insert(event);
        verify(publisher).publishPersisted(event);
    }

    @Test
    void shouldReturnExistingBusinessEventWithoutAllocatingAnotherSequence() {
        WorkflowRunEvent existing = WorkflowRunEvent.builder().workflowRunUuid("run").sequence(2L).build();
        when(events.lockRunForEventAppend("run")).thenReturn(WorkflowRun.builder().workflowRunUuid("run").build());
        when(events.selectByRunAndEventKey("run", "run.created")).thenReturn(existing);

        assertThat(service().record("run", "run.created", "run.created", null, "PENDING", 1, null, "trace"))
                .isSameAs(existing);
        verify(events, never()).allocateNextSequence(any());
        verify(events, never()).insert(any(WorkflowRunEvent.class));
        verify(publisher, never()).publishPersisted(any());
    }

    @Test
    void shouldReplayOnlyLaterEventsInSequenceOrder() {
        List<WorkflowRunEvent> replay = List.of(WorkflowRunEvent.builder().sequence(3L).build(), WorkflowRunEvent.builder().sequence(4L).build());
        when(events.selectAfterSequence("run", 2L)).thenReturn(replay);

        assertThat(service().findAfter("run", 2L)).isEqualTo(replay);
        verify(events).selectAfterSequence("run", 2L);
    }

    private WorkflowRunEventService service() {
        return new WorkflowRunEventService(events, publisher, new ObjectMapper().findAndRegisterModules());
    }
}
