package com.example.gameworkbench.director.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum DirectorRunStatus {
    PENDING, RUNNING, WAITING_EXPERIMENT, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELED;

    private static final Map<DirectorRunStatus, Set<DirectorRunStatus>> TRANSITIONS = Map.of(
        PENDING, EnumSet.of(RUNNING, CANCELED),
        RUNNING, EnumSet.of(WAITING_EXPERIMENT, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELED),
        WAITING_EXPERIMENT, EnumSet.of(RUNNING, FAILED, CANCELED),
        WAITING_APPROVAL, EnumSet.of(RUNNING, SUCCEEDED, FAILED, CANCELED),
        SUCCEEDED, EnumSet.noneOf(DirectorRunStatus.class),
        FAILED, EnumSet.noneOf(DirectorRunStatus.class),
        CANCELED, EnumSet.noneOf(DirectorRunStatus.class));

    public boolean canTransitionTo(DirectorRunStatus target) { return TRANSITIONS.get(this).contains(target); }
    public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELED; }
}
