package com.example.gameworkbench.generation;

public enum GenerationRunStatus {
    VALIDATING, READY_TO_BUILD, BUILDING, PLAYTESTING, AWAITING_APPROVAL,
    APPROVED, RELEASED, REJECTED, FAILED, CANCELLED;

    public boolean terminal() {
        return this == RELEASED || this == REJECTED || this == FAILED || this == CANCELLED;
    }
}
