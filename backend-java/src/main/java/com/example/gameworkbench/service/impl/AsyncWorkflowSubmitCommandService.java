package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;

import lombok.RequiredArgsConstructor;

/** Performs only the short, durable submit transaction; it does not contact a broker or runner. */
@Service
@RequiredArgsConstructor
public class AsyncWorkflowSubmitCommandService {

    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final OutboxEventMapper outboxEventMapper;

    @Transactional
    public WorkflowRun create(WorkflowRun workflowRun, List<WorkflowStepRun> stepRuns, String eventPayload, String traceId) {
        workflowRun.setTraceId(traceId);
        workflowRunMapper.insert(workflowRun);
        for (WorkflowStepRun stepRun : stepRuns) {
            stepRun.setWorkflowRunId(workflowRun.getId());
            workflowStepRunMapper.insert(stepRun);
        }

        LocalDateTime now = LocalDateTime.now();
        outboxEventMapper.insert(OutboxEvent.builder()
                .eventUuid(UUID.randomUUID().toString())
                .aggregateType("WORKFLOW_RUN")
                .aggregateUuid(workflowRun.getWorkflowRunUuid())
                .workflowRunId(workflowRun.getId())
                .workflowRunUuid(workflowRun.getWorkflowRunUuid())
                .eventType("WORKFLOW_RUN_REQUESTED")
                .payloadJson(eventPayload)
                .schemaVersion("workflow-run-requested/1")
                .status("PENDING")
                .publishAttempt(0)
                .nextAttemptAt(now)
                .traceId(traceId)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return workflowRun;
    }
}
