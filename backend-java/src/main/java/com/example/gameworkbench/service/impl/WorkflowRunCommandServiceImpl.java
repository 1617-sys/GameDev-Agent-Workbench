package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.OutboxEvent;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.OutboxEventMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.messaging.WorkflowRunMessage;
import com.example.gameworkbench.service.WorkflowRunCommandService;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;
import com.example.gameworkbench.vo.workflow.WorkflowRunCommandVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowRunCommandServiceImpl implements WorkflowRunCommandService {
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final WorkflowRunEventRecorder workflowRunEventRecorder;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WorkflowRunCommandVO cancel(Long userId, String workflowRunUuid) {
        WorkflowRun before = requireOwned(userId, workflowRunUuid);
        if ("CANCELED".equals(before.getStatus())) return response(before, true);
        LocalDateTime now = LocalDateTime.now();
        if (workflowRunMapper.cancelIfActive(userId, workflowRunUuid, now) != 1) {
            WorkflowRun current = requireOwned(userId, workflowRunUuid);
            if ("CANCELED".equals(current.getStatus())) return response(current, true);
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
        WorkflowRun current = requireOwned(userId, workflowRunUuid);
        workflowRunEventRecorder.record(workflowRunUuid, "run.terminal", "command.cancel." + current.getAttempt(), null,
                "CANCELED", current.getAttempt(), null, UUID.randomUUID().toString());
        return response(current, false);
    }

    @Override
    @Transactional
    public WorkflowRunCommandVO retry(Long userId, String workflowRunUuid) {
        WorkflowRun before = requireOwned(userId, workflowRunUuid);
        if ("PENDING".equals(before.getStatus()) && "RETRY".equals(before.getCommandKey())) return response(before, true);
        if (!"FAILED".equals(before.getStatus()) && !"TIMEOUT".equals(before.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
        LocalDateTime now = LocalDateTime.now();
        if (workflowRunMapper.beginManualRetry(userId, workflowRunUuid, now) != 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
        WorkflowRun retried = requireOwned(userId, workflowRunUuid);
        for (WorkflowStepRun step : workflowStepRunMapper.selectByWorkflowRunUuid(workflowRunUuid)) {
            if (!"SUCCESS".equals(step.getStatus())) {
                step.setStatus("PENDING");
                step.setAttempt(retried.getAttempt());
                step.setErrorMessage(null);
                step.setStartedAt(null);
                step.setFinishedAt(null);
                step.setUpdatedAt(now);
                workflowStepRunMapper.updateById(step);
            }
        }
        String traceId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        outboxEventMapper.insert(OutboxEvent.builder().eventUuid(eventId).aggregateType("WORKFLOW_RUN")
                .aggregateUuid(workflowRunUuid).workflowRunId(retried.getId()).workflowRunUuid(workflowRunUuid)
                .eventType("WORKFLOW_RUN_RETRY_REQUESTED").payloadJson(payload(new WorkflowRunMessage(1, eventId, eventId,
                        workflowRunUuid, retried.getAttempt(), traceId, now))).schemaVersion("workflow-run-requested/1")
                .status("PENDING").publishAttempt(0).nextAttemptAt(now).traceId(traceId).createdAt(now).updatedAt(now).build());
        workflowRunEventRecorder.record(workflowRunUuid, "run.retry-requested", "command.retry." + retried.getAttempt(), null,
                "PENDING", retried.getAttempt(), null, traceId);
        return response(retried, false);
    }

    private WorkflowRun requireOwned(Long userId, String uuid) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        WorkflowRun run = workflowRunMapper.selectReadModelByUserIdAndWorkflowRunUuid(userId, uuid);
        if (run == null) throw new BusinessException(ErrorCode.WORKFLOW_RUN_NOT_FOUND);
        return run;
    }
    private String payload(WorkflowRunMessage message) {
        try { return objectMapper.writeValueAsString(message); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.SYSTEM_ERROR); }
    }
    private WorkflowRunCommandVO response(WorkflowRun run, boolean reused) {
        return WorkflowRunCommandVO.builder().workflowRunUuid(run.getWorkflowRunUuid()).status(run.getStatus())
                .attempt(run.getAttempt()).reused(reused).build();
    }
}
