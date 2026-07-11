package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventCommandServiceTest {

    @Mock private WorkflowRunMapper runs;
    @Mock private WorkflowStepRunMapper steps;
    @Mock private OutboxEventMapper outboxEvents;

    @Test
    void createsInitialPendingOutboxOnlyAfterRunAndStepPlanArePrepared() {
        AsyncWorkflowSubmitCommandService service = new AsyncWorkflowSubmitCommandService(runs, steps, outboxEvents, mock(WorkflowRunEventRecorder.class));
        WorkflowRun run = WorkflowRun.builder().workflowRunUuid("run").build();
        WorkflowStepRun step = WorkflowStepRun.builder().workflowRunUuid("run").stepRunUuid("step").build();
        doAnswer(invocation -> {
            invocation.<WorkflowRun>getArgument(0).setId(3L);
            return 1;
        }).when(runs).insert(any(WorkflowRun.class));

        service.create(run, List.of(step), "{\"workflowRunUuid\":\"run\"}", "trace");

        assertThat(step.getWorkflowRunId()).isEqualTo(3L);
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEvents).insert(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(event.getValue().getEventType()).isEqualTo("WORKFLOW_RUN_REQUESTED");
    }
}
