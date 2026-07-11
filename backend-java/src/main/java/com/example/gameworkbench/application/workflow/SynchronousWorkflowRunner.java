package com.example.gameworkbench.application.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.WorkflowRunStatus;
import com.example.gameworkbench.common.enums.WorkflowStepRunStatus;
import com.example.gameworkbench.domain.workflow.WorkflowStatusPolicy;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.entity.AgentArtifact;

@Service
public class SynchronousWorkflowRunner implements WorkflowRunner {
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final WorkflowStepPlanParser planParser;
    private final List<WorkflowStepExecutor> executors;
    private final ArtifactWriter artifactWriter;

    public SynchronousWorkflowRunner(WorkflowRunMapper workflowRunMapper, WorkflowStepRunMapper workflowStepRunMapper,
            WorkflowStepPlanParser planParser, List<WorkflowStepExecutor> executors, ArtifactWriter artifactWriter) {
        this.workflowRunMapper = workflowRunMapper;
        this.workflowStepRunMapper = workflowStepRunMapper;
        this.planParser = planParser;
        this.executors = executors;
        this.artifactWriter = artifactWriter;
    }

    @Override
    public void run(String workflowRunUuid, String projectUuid, WorkflowExecutionListener listener) {
        WorkflowExecutionListener safeListener = listener == null ? WorkflowExecutionListener.noop() : listener;
        WorkflowRun run = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                .eq(WorkflowRun::getWorkflowRunUuid, workflowRunUuid));
        if (run == null || isTerminal(run.getStatus())) throw new IllegalStateException("Workflow run is not executable");
        List<WorkflowStepPlan> plans = planParser.parse(run.getWorkflowDefinitionSnapshot());
        WorkflowExecutionContext context = new WorkflowExecutionContext(run, projectUuid, run.getInputContent(), plans);
        safe(safeListener, "WORKFLOW_STARTED", null);
        for (WorkflowStepPlan plan : plans) {
            WorkflowStepRun step = findOrCreate(run, plan);
            if (WorkflowStepRunStatus.SUCCESS.name().equals(step.getStatus())) {
                context.recordCompletedOutput(plan.stepKey(), new StepOutput(step.getOutputSnapshot(), null, null, null));
                continue;
            }
            try {
                WorkflowStatusPolicy.requireTransition(WorkflowStepRunStatus.PENDING, WorkflowStepRunStatus.RUNNING,
                        context.dependenciesSatisfied(plan));
                step.setStatus(WorkflowStepRunStatus.RUNNING.name()); step.setStartedAt(LocalDateTime.now());
                requireUpdated(workflowStepRunMapper.updateById(step), "step claim"); safe(safeListener, "STEP_STARTED", plan.stepKey());
                WorkflowStepExecutor executor = executors.stream().filter(e -> e.supports(plan)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No executor for step: " + plan.stepKey()));
                StepExecutionResult result = executor.execute(context, plan);
                StepOutput output = artifactWriter.write(context, plan, step, result);
                step.setStatus(WorkflowStepRunStatus.SUCCESS.name()); step.setOutputSnapshot(output.content());
                step.setFinishedAt(LocalDateTime.now()); requireUpdated(workflowStepRunMapper.updateById(step), "step success");
                context.recordCompletedOutput(plan.stepKey(), output); safe(safeListener, "STEP_SUCCEEDED", plan.stepKey());
            } catch (Exception exception) {
                step.setStatus(WorkflowStepRunStatus.FAILED.name()); step.setErrorMessage("Workflow step failed");
                step.setFinishedAt(LocalDateTime.now()); requireUpdated(workflowStepRunMapper.updateById(step), "step failure");
                run.setStatus(WorkflowRunStatus.FAILED.name()); requireUpdated(workflowRunMapper.updateById(run), "workflow failure");
                safe(safeListener, "STEP_FAILED", plan.stepKey()); safe(safeListener, "WORKFLOW_COMPLETED", null);
                throw exception;
            }
        }
        run.setStatus(WorkflowRunStatus.SUCCESS.name()); requireUpdated(workflowRunMapper.updateById(run), "workflow success");
        safe(safeListener, "WORKFLOW_COMPLETED", null);
    }

    private WorkflowStepRun findOrCreate(WorkflowRun run, WorkflowStepPlan plan) {
        return workflowStepRunMapper.selectByWorkflowRunUuid(run.getWorkflowRunUuid()).stream()
                .filter(step -> plan.stepKey().equals(step.getStepKey())).findFirst().orElseGet(() -> {
                    WorkflowStepRun step = WorkflowStepRun.builder().stepRunUuid(UUID.randomUUID().toString())
                            .workflowRunId(run.getId()).workflowRunUuid(run.getWorkflowRunUuid())
                            .definitionVersionId(run.getWorkflowDefinitionVersionId()).stepKey(plan.stepKey())
                            .stepOrder(plan.stepOrder()).agentType(plan.agentType().name())
                            .artifactType(plan.artifactType().name()).status(WorkflowStepRunStatus.PENDING.name())
                            .attempt(1).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
                    workflowStepRunMapper.insert(step); return step;
                });
    }
    private boolean isTerminal(String status) { return WorkflowRunStatus.SUCCESS.name().equals(status) || WorkflowRunStatus.FAILED.name().equals(status) || WorkflowRunStatus.CANCELED.name().equals(status); }
    private void requireUpdated(int affectedRows, String operation) { if (affectedRows != 1) throw new IllegalStateException("Workflow state update lost ownership: " + operation); }
    private void safe(WorkflowExecutionListener listener, String type, String stepKey) { try { listener.onEvent(type, stepKey); } catch (Exception ignored) { } }
}
