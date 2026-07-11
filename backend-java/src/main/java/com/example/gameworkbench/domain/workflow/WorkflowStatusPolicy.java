package com.example.gameworkbench.domain.workflow;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.example.gameworkbench.common.enums.WorkflowRunStatus;
import com.example.gameworkbench.common.enums.WorkflowStepRunStatus;

/**
 * Defines the legal lifecycle transitions for workflow and workflow-step runs.
 *
 * <p>The current synchronous workflow service intentionally remains on its legacy
 * {@code RUNNING -> SUCCESS/FAILED} path. Future runners must validate transitions
 * through this policy before persisting a status change.</p>
 */
public final class WorkflowStatusPolicy {

    private static final Map<WorkflowRunStatus, Set<WorkflowRunStatus>> WORKFLOW_TRANSITIONS =
            transitions(WorkflowRunStatus.class, Map.of(
                    WorkflowRunStatus.PENDING, EnumSet.of(
                            WorkflowRunStatus.QUEUED,
                            WorkflowRunStatus.CANCELED),
                    WorkflowRunStatus.QUEUED, EnumSet.of(
                            WorkflowRunStatus.RUNNING,
                            WorkflowRunStatus.CANCELED),
                    WorkflowRunStatus.RUNNING, EnumSet.of(
                            WorkflowRunStatus.SUCCESS,
                            WorkflowRunStatus.FAILED,
                            WorkflowRunStatus.RETRY_WAIT,
                            WorkflowRunStatus.TIMEOUT,
                            WorkflowRunStatus.CANCELED),
                    WorkflowRunStatus.RETRY_WAIT, EnumSet.of(
                            WorkflowRunStatus.QUEUED,
                            WorkflowRunStatus.CANCELED),
                    WorkflowRunStatus.FAILED, EnumSet.of(WorkflowRunStatus.QUEUED),
                    WorkflowRunStatus.TIMEOUT, EnumSet.of(WorkflowRunStatus.QUEUED)
            ));

    private static final Map<WorkflowStepRunStatus, Set<WorkflowStepRunStatus>> STEP_TRANSITIONS =
            transitions(WorkflowStepRunStatus.class, Map.of(
                    WorkflowStepRunStatus.PENDING, EnumSet.of(
                            WorkflowStepRunStatus.RUNNING,
                            WorkflowStepRunStatus.CANCELED,
                            WorkflowStepRunStatus.SKIPPED),
                    WorkflowStepRunStatus.RUNNING, EnumSet.of(
                            WorkflowStepRunStatus.SUCCESS,
                            WorkflowStepRunStatus.FAILED,
                            WorkflowStepRunStatus.TIMEOUT,
                            WorkflowStepRunStatus.CANCELED),
                    WorkflowStepRunStatus.FAILED, EnumSet.of(WorkflowStepRunStatus.PENDING),
                    WorkflowStepRunStatus.TIMEOUT, EnumSet.of(WorkflowStepRunStatus.PENDING)
            ));

    private WorkflowStatusPolicy() {
    }

    public static boolean canTransition(WorkflowRunStatus from, WorkflowRunStatus to) {
        return allows(WORKFLOW_TRANSITIONS, from, to);
    }

    public static void requireTransition(WorkflowRunStatus from, WorkflowRunStatus to) {
        if (!canTransition(from, to)) {
            throw illegalTransition("workflow run", from, to);
        }
    }

    public static boolean canTransition(
            WorkflowStepRunStatus from,
            WorkflowStepRunStatus to,
            boolean dependenciesSatisfied
    ) {
        return allows(STEP_TRANSITIONS, from, to)
                && (to != WorkflowStepRunStatus.RUNNING || dependenciesSatisfied);
    }

    public static void requireTransition(
            WorkflowStepRunStatus from,
            WorkflowStepRunStatus to,
            boolean dependenciesSatisfied
    ) {
        if (!canTransition(from, to, dependenciesSatisfied)) {
            if (from == WorkflowStepRunStatus.PENDING
                    && to == WorkflowStepRunStatus.RUNNING
                    && !dependenciesSatisfied) {
                throw new IllegalStateException(
                        "Cannot transition workflow step run from PENDING to RUNNING before all dependencies succeed");
            }
            throw illegalTransition("workflow step run", from, to);
        }
    }

    private static <S extends Enum<S>> boolean allows(Map<S, Set<S>> transitions, S from, S to) {
        return from != null && to != null && transitions.getOrDefault(from, Set.of()).contains(to);
    }

    private static IllegalStateException illegalTransition(String subject, Enum<?> from, Enum<?> to) {
        return new IllegalStateException(
                "Cannot transition " + subject + " from " + from + " to " + to);
    }

    private static <S extends Enum<S>> Map<S, Set<S>> transitions(
            Class<S> statusType,
            Map<S, Set<S>> configuredTransitions
    ) {
        Map<S, Set<S>> result = new EnumMap<>(statusType);
        result.putAll(configuredTransitions);
        return Map.copyOf(result);
    }
}
