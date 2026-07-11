package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.application.workflow.WorkflowExecutionListener;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.vo.demo.GameDemoStreamEventVO;

import lombok.extern.slf4j.Slf4j;

/** Translates runner events to the legacy Demo SSE protocol. */
@Slf4j
final class DemoWorkflowExecutionListener implements WorkflowExecutionListener {
    private final SseEmitter emitter;
    private final String projectUuid;
    private final String workflowRunUuid;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final AgentArtifactMapper agentArtifactMapper;
    private final AgentRunMapper agentRunMapper;

    DemoWorkflowExecutionListener(SseEmitter emitter, String projectUuid, String workflowRunUuid,
            WorkflowStepRunMapper workflowStepRunMapper, AgentArtifactMapper agentArtifactMapper,
            AgentRunMapper agentRunMapper) {
        this.emitter = emitter;
        this.projectUuid = projectUuid;
        this.workflowRunUuid = workflowRunUuid;
        this.workflowStepRunMapper = workflowStepRunMapper;
        this.agentArtifactMapper = agentArtifactMapper;
        this.agentRunMapper = agentRunMapper;
    }

    @Override
    public void onEvent(String type, String stepKey) {
        if ("WORKFLOW_STARTED".equals(type)) {
            send("WORKFLOW_STARTED", "RUNNING", "Demo workflow started", null, null, null);
            return;
        }
        if (stepKey == null || (!"STEP_STARTED".equals(type) && !"STEP_SUCCEEDED".equals(type)
                && !"STEP_FAILED".equals(type))) {
            return;
        }
        WorkflowStepRun step = workflowStepRunMapper.selectByWorkflowRunUuid(workflowRunUuid).stream()
                .filter(candidate -> stepKey.equals(candidate.getStepKey())).findFirst().orElse(null);
        if (step == null) {
            log.warn("[DemoStream] runner event references missing step workflowRunUuid={} stepKey={}", workflowRunUuid, stepKey);
            return;
        }
        AgentArtifact artifact = "STEP_SUCCEEDED".equals(type)
                ? agentArtifactMapper.selectLatestByStepRunId(step.getId()) : null;
        AgentRun agentRun = step.getAgentRunId() == null ? null : agentRunMapper.selectById(step.getAgentRunId());
        String status = "STEP_STARTED".equals(type) ? "RUNNING" : "STEP_SUCCEEDED".equals(type) ? "SUCCESS" : "FAILED";
        String message = "STEP_STARTED".equals(type) ? "Running " + step.getAgentType()
                : "STEP_SUCCEEDED".equals(type) ? "Completed " + step.getAgentType() : "Failed " + step.getAgentType();
        send(step.getAgentType(), status, message, agentRun == null ? null : agentRun.getRunUuid(),
                artifact == null ? null : artifact.getArtifactUuid(), artifact == null ? null : artifact.getContent());
    }

    private void send(String stage, String status, String message, String agentRunUuid, String artifactUuid, Object data) {
        try {
            emitter.send(SseEmitter.event().name("progress").data(GameDemoStreamEventVO.builder()
                    .stage(stage).status(status).message(message).projectUuid(projectUuid)
                    .workflowRunUuid(workflowRunUuid).agentRunUuid(agentRunUuid).artifactUuid(artifactUuid)
                    .data(data).eventTime(LocalDateTime.now()).build()));
        } catch (Exception exception) {
            log.warn("[DemoStream] SSE listener send failed workflowRunUuid={} stage={} status={}",
                    workflowRunUuid, stage, status, exception);
        }
    }
}
