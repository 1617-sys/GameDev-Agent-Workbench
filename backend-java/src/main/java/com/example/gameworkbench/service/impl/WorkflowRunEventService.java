package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowRunEvent;
import com.example.gameworkbench.mapper.WorkflowRunEventMapper;
import com.example.gameworkbench.service.WorkflowRunEventPublisher;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowRunEventService implements WorkflowRunEventRecorder {
    private final WorkflowRunEventMapper workflowRunEventMapper;
    private final WorkflowRunEventPublisher workflowRunEventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WorkflowRunEvent record(String workflowRunUuid, String eventType, String eventKey, String stepKey,
                                   String status, Integer attempt, String artifactUuid, String traceId) {
        WorkflowRun lockedRun = workflowRunEventMapper.lockRunForEventAppend(workflowRunUuid);
        if (lockedRun == null) {
            throw new IllegalStateException("Cannot append event for missing workflow run");
        }
        WorkflowRunEvent existing = workflowRunEventMapper.selectByRunAndEventKey(workflowRunUuid, eventKey);
        if (existing != null) {
            return existing;
        }
        if (workflowRunEventMapper.allocateNextSequence(workflowRunUuid) != 1) {
            throw new IllegalStateException("Cannot allocate workflow event sequence");
        }
        Long sequence = workflowRunEventMapper.selectCurrentSequence(workflowRunUuid);
        if (sequence == null || sequence < 1) {
            throw new IllegalStateException("Workflow event sequence was not allocated");
        }
        WorkflowRunEvent event = WorkflowRunEvent.builder()
                .eventUuid(UUID.randomUUID().toString()).workflowRunUuid(workflowRunUuid).sequence(sequence)
                .eventType(eventType).eventKey(eventKey).stepKey(stepKey).status(status).attempt(attempt)
                .artifactUuid(artifactUuid).payloadJson(payload(workflowRunUuid, eventType, sequence, stepKey, status, attempt, artifactUuid))
                .traceId(traceId).occurredAt(LocalDateTime.now()).build();
        workflowRunEventMapper.insert(event);
        publishAfterCommit(event);
        return event;
    }

    public List<WorkflowRunEvent> findAfter(String workflowRunUuid, long afterSequence) {
        return workflowRunEventMapper.selectAfterSequence(workflowRunUuid, Math.max(0, afterSequence));
    }

    private String payload(String runUuid, String eventType, Long sequence, String stepKey, String status,
                           Integer attempt, String artifactUuid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowRunUuid", runUuid);
        payload.put("eventType", eventType);
        payload.put("sequence", sequence);
        if (stepKey != null) payload.put("stepKey", stepKey);
        if (status != null) payload.put("status", status);
        if (attempt != null) payload.put("attempt", attempt);
        if (artifactUuid != null) payload.put("artifactUuid", artifactUuid);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize safe workflow event payload", exception);
        }
    }

    private void publishAfterCommit(WorkflowRunEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            workflowRunEventPublisher.publishPersisted(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                workflowRunEventPublisher.publishPersisted(event);
            }
        });
    }
}
