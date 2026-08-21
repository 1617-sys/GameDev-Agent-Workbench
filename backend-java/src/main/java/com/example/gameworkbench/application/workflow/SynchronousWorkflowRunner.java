package com.example.gameworkbench.application.workflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.WorkflowRunStatus;
import com.example.gameworkbench.common.enums.WorkflowStepRunStatus;
import com.example.gameworkbench.domain.workflow.WorkflowStatusPolicy;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.service.WorkflowRunEventRecorder;

/**
 * 基于运行快照串行执行工作流步骤的传输无关 Runner。
 *
 * <p>Runner 只理解持久化的 WorkflowRun、步骤依赖、Executor、EvaluationHook 和 ArtifactWriter，
 * 不感知 HTTP、SSE 或 RabbitMQ。运行使用提交时冻结的 definition snapshot，避免配置变化
 * 改写历史运行语义。</p>
 *
 * <p>已经成功的步骤在恢复时会被复用。每个步骤仍需保证外部副作用幂等，因为进程可能在
 * 外部调用成功但步骤成功状态落库前退出。</p>
 */
@Service
public class SynchronousWorkflowRunner implements WorkflowRunner {
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper workflowStepRunMapper;
    private final WorkflowStepPlanParser planParser;
    private final List<WorkflowStepExecutor> executors;
    private final ArtifactWriter artifactWriter;
    private final List<WorkflowEvaluationHook> evaluationHooks;
    private final WorkflowRunEventRecorder workflowRunEventRecorder;

    public SynchronousWorkflowRunner(WorkflowRunMapper workflowRunMapper, WorkflowStepRunMapper workflowStepRunMapper,
            WorkflowStepPlanParser planParser, List<WorkflowStepExecutor> executors, ArtifactWriter artifactWriter) {
        this(workflowRunMapper, workflowStepRunMapper, planParser, executors, artifactWriter, List.of(), noopRecorder());
    }

    public SynchronousWorkflowRunner(WorkflowRunMapper workflowRunMapper, WorkflowStepRunMapper workflowStepRunMapper,
            WorkflowStepPlanParser planParser, List<WorkflowStepExecutor> executors, ArtifactWriter artifactWriter,
            List<WorkflowEvaluationHook> evaluationHooks) {
        this(workflowRunMapper, workflowStepRunMapper, planParser, executors, artifactWriter, evaluationHooks, noopRecorder());
    }

    @Autowired
    public SynchronousWorkflowRunner(WorkflowRunMapper workflowRunMapper, WorkflowStepRunMapper workflowStepRunMapper,
            WorkflowStepPlanParser planParser, List<WorkflowStepExecutor> executors, ArtifactWriter artifactWriter,
            List<WorkflowEvaluationHook> evaluationHooks, WorkflowRunEventRecorder workflowRunEventRecorder) {
        this.workflowRunMapper = workflowRunMapper;
        this.workflowStepRunMapper = workflowStepRunMapper;
        this.planParser = planParser;
        this.executors = executors;
        this.artifactWriter = artifactWriter;
        this.evaluationHooks = evaluationHooks;
        this.workflowRunEventRecorder = workflowRunEventRecorder;
    }

    @Override
    public void run(String workflowRunUuid, String projectUuid, WorkflowExecutionListener listener) {
        WorkflowExecutionListener safeListener = listener == null ? WorkflowExecutionListener.noop() : listener;
        WorkflowRun run = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                .eq(WorkflowRun::getWorkflowRunUuid, workflowRunUuid));
        if (run == null || isTerminal(run.getStatus())) throw new IllegalStateException("Workflow run is not executable");
        List<WorkflowStepPlan> plans = planParser.parse(run.getWorkflowDefinitionSnapshot());
        WorkflowExecutionContext context = new WorkflowExecutionContext(run, projectUuid, run.getInputContent(), plans);
        heartbeat(workflowRunUuid);
        safe(safeListener, "WORKFLOW_STARTED", null);
        for (WorkflowStepPlan plan : plans) {
            WorkflowStepRun step = findOrCreate(run, plan);
            if (WorkflowStepRunStatus.SUCCESS.name().equals(step.getStatus())) {
                // FAILURE: 恢复执行时跳过已成功步骤，并把其输出重新装入上下文。
                // 这使后续依赖步骤可以继续使用已持久化结果。
                context.recordCompletedOutput(plan.stepKey(), new StepOutput(step.getOutputSnapshot(), null, null, null));
                continue;
            }
            try {
                heartbeat(workflowRunUuid);
                WorkflowStatusPolicy.requireTransition(WorkflowStepRunStatus.PENDING, WorkflowStepRunStatus.RUNNING,
                        context.dependenciesSatisfied(plan));
                step.setStatus(WorkflowStepRunStatus.RUNNING.name()); step.setStartedAt(LocalDateTime.now());
                requireUpdated(workflowStepRunMapper.updateById(step), "step claim"); safe(safeListener, "STEP_STARTED", plan.stepKey());
                recordStep(run, step, "RUNNING");
                WorkflowStepExecutor executor = executors.stream().filter(e -> e.supports(plan)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No executor for step: " + plan.stepKey()));
                StepExecutionResult result = executor.execute(context, plan);
                for (WorkflowEvaluationHook hook : evaluationHooks) {
                    if (hook.supports(plan)) result = result.withEvaluation(hook.evaluate(context, plan, result));
                }
                StepOutput output = artifactWriter.write(context, plan, step, result);
                step.setStatus(WorkflowStepRunStatus.SUCCESS.name()); step.setAgentRunId(result.agentRunId()); step.setOutputSnapshot(output.content());
                step.setSchemaKey(output.schemaKey()); step.setSchemaVersion(output.schemaVersion()); step.setValidationSummary(result.evaluation().summary());
                step.setFinishedAt(LocalDateTime.now()); requireUpdated(workflowStepRunMapper.updateById(step), "step success");
                recordStep(run, step, "SUCCESS");
                context.recordCompletedOutput(plan.stepKey(), output); safe(safeListener, "STEP_SUCCEEDED", plan.stepKey());
                heartbeat(workflowRunUuid);
            } catch (Exception exception) {
                step.setStatus(WorkflowStepRunStatus.FAILED.name()); step.setErrorMessage(exception.getMessage() == null ? "Workflow step failed" : exception.getMessage());
                if (exception instanceof WorkflowEvaluationException) step.setValidationSummary(exception.getMessage());
                step.setFinishedAt(LocalDateTime.now()); requireUpdated(workflowStepRunMapper.updateById(step), "step failure");
                // TODO(reliability): 这里提前写入 FAILED 会与 MQ Consumer 的 RUNNING -> RETRY_WAIT
                // 条件迁移冲突。重构时应让上层执行边界依据错误分类决定重试或最终失败。
                run.setStatus(WorkflowRunStatus.FAILED.name()); requireUpdated(workflowRunMapper.updateById(run), "workflow failure");
                recordStep(run, step, "FAILED");
                recordRun(run, "run.terminal", "FAILED");
                safe(safeListener, "STEP_FAILED", plan.stepKey()); safe(safeListener, "WORKFLOW_COMPLETED", null);
                throw exception;
            }
        }
        run.setStatus(WorkflowRunStatus.SUCCESS.name()); requireUpdated(workflowRunMapper.updateById(run), "workflow success");
        recordRun(run, "run.terminal", "SUCCESS");
        safe(safeListener, "WORKFLOW_COMPLETED", null);
    }

    private void heartbeat(String workflowRunUuid) { workflowRunMapper.touchHeartbeat(workflowRunUuid, LocalDateTime.now()); }

    private WorkflowStepRun findOrCreate(WorkflowRun run, WorkflowStepPlan plan) {
        return workflowStepRunMapper.selectByWorkflowRunUuid(run.getWorkflowRunUuid()).stream()
                .filter(step -> plan.stepKey().equals(step.getStepKey())).findFirst().orElseGet(() -> {
                    WorkflowStepRun step = WorkflowStepRun.builder().stepRunUuid(UUID.randomUUID().toString())
                            .workflowRunId(run.getId()).workflowRunUuid(run.getWorkflowRunUuid())
                            .definitionVersionId(run.getWorkflowDefinitionVersionId()).stepKey(plan.stepKey())
                            .stepOrder(plan.stepOrder()).agentType(plan.agentType().name())
                            .artifactType(plan.artifactType().name()).status(WorkflowStepRunStatus.PENDING.name())
                            .attempt(1).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
                    workflowStepRunMapper.insert(step);
                    recordStep(run, step, "PENDING");
                    return step;
                });
    }
    private void recordStep(WorkflowRun run, WorkflowStepRun step, String status) {
        workflowRunEventRecorder.record(run.getWorkflowRunUuid(), "step.status-changed",
                "step." + step.getStepKey() + "." + step.getAttempt() + "." + status, step.getStepKey(), status,
                step.getAttempt(), null, run.getTraceId());
    }
    private void recordRun(WorkflowRun run, String eventType, String status) {
        workflowRunEventRecorder.record(run.getWorkflowRunUuid(), eventType,
                "run." + status + "." + run.getAttempt(), null, status, run.getAttempt(), null, run.getTraceId());
    }
    private static WorkflowRunEventRecorder noopRecorder() { return (a, b, c, d, e, f, g, h) -> null; }
    private boolean isTerminal(String status) { return WorkflowRunStatus.SUCCESS.name().equals(status) || WorkflowRunStatus.FAILED.name().equals(status) || WorkflowRunStatus.CANCELED.name().equals(status); }
    private void requireUpdated(int affectedRows, String operation) { if (affectedRows != 1) throw new IllegalStateException("Workflow state update lost ownership: " + operation); }
    private void safe(WorkflowExecutionListener listener, String type, String stepKey) { try { listener.onEvent(type, stepKey); } catch (Exception ignored) { } }
}
