package com.example.gameworkbench.generation;

public enum GenerationRunStatus {
    VALIDATING, BUILDING, PLAYTESTING, AWAITING_APPROVAL, APPROVED, REJECTED, FAILED, CANCELLED;

    public boolean terminal() {
        return this == APPROVED || this == REJECTED || this == FAILED || this == CANCELLED;
    }
}
