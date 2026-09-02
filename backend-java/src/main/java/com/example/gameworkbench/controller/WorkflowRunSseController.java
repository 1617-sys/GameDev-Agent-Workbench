package com.example.gameworkbench.controller;

import com.example.gameworkbench.service.impl.WorkflowRunSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/workflow-runs")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'workflow-runs.manage')")
public class WorkflowRunSseController {
    private final WorkflowRunSubscriptionService workflowRunSubscriptionService;

    @GetMapping(value = "/{workflowRunUuid}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal Long userId,
            @PathVariable String workflowRunUuid,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return workflowRunSubscriptionService.subscribe(userId, workflowRunUuid, lastEventId);
    }
}
