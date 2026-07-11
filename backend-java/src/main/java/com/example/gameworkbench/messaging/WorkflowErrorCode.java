package com.example.gameworkbench.messaging;

public enum WorkflowErrorCode {
    INVALID_REQUEST(false), PROMPT_CONFIGURATION(false), OUTPUT_VALIDATION(false),
    PROVIDER_RATE_LIMIT(true), NETWORK_TIMEOUT(true), INFRASTRUCTURE(true);

    private final boolean retryable;
    WorkflowErrorCode(boolean retryable) { this.retryable = retryable; }
    public boolean retryable() { return retryable; }
}
