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
import com.example.gameworkbench.service.WorkflowRunEventRecorder;

import lombok.RequiredArgsConstructor;

/**
 * 负责异步提交的短事务边界。
 *
 * <p>WorkflowRun、StepRun、首个运行事件和 OutboxEvent 必须同时提交或同时回滚。
 * 该类不连接 Broker，也不调用 Runner；消息发布由事务提交后的 Outbox Publisher 完成。</p>
 */
@Service
@RequiredArgsConstructor
public class AsyncWorkflowSubmitCommandService {

    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final WorkflowRunEventRecorder workflowRunEventRecorder;

    @Transactional
    public WorkflowRun create(WorkflowRun workflowRun, List<WorkflowStepRun> stepRuns, String eventPayload, String traceId) {
        workflowRun.setTraceId(traceId);
        workflowRunMapper.insert(workflowRun);
        for (WorkflowStepRun stepRun : stepRuns) {
            stepRun.setWorkflowRunId(workflowRun.getId());
            workflowStepRunMapper.insert(stepRun);
        }
        workflowRunEventRecorder.record(workflowRun.getWorkflowRunUuid(), "run.created", "run.created", null,
                workflowRun.getStatus(), workflowRun.getAttempt(), null, traceId);

        LocalDateTime now = LocalDateTime.now();
        // INVARIANT: 业务状态和投递意图处于同一个数据库事务。
        // 即使进程在事务提交后立即崩溃，调度器仍能从 Outbox 恢复待发布事件。
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
