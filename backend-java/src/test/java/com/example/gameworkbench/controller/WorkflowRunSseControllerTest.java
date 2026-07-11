package com.example.gameworkbench.controller;

import com.example.gameworkbench.service.impl.WorkflowRunSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRunSseControllerTest {
    @Test
    void shouldDelegateV1SubscriptionWithLastEventId() {
        WorkflowRunSubscriptionService service = mock(WorkflowRunSubscriptionService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(service.subscribe(7L, "run", "4")).thenReturn(emitter);

        assertThat(new WorkflowRunSseController(service).subscribe(7L, "run", "4")).isSameAs(emitter);
        verify(service).subscribe(7L, "run", "4");
    }
}
