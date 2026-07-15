package com.example.gameworkbench.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    INVALID_PARAM(40001, "Invalid request parameter", "Validation"),
    AGENT_TYPE_REQUIRED(40001, "Agent type is required", "Agent request validation"),
    USERNAME_ALREADY_EXISTS(40002, "Username already exists", "Register"),
    INVALID_USERNAME_OR_PASSWORD(40003, "Invalid username or password", "Login"),
    ACCOUNT_DISABLED(40004, "Account is disabled", "Login or current user"),

    UNAUTHORIZED(40101, "Unauthorized", "Authentication"),
    TOKEN_INVALID_OR_EXPIRED(40101, "Token is invalid or expired", "JWT authentication"),

    FORBIDDEN_PROJECT_ACCESS(40301, "Forbidden project access", "Project detail"),
    FORBIDDEN_PROJECT_UPDATE(40301, "Forbidden project update", "Project update"),
    FORBIDDEN_ARTIFACT_ACCESS(40301, "Forbidden artifact access", "Artifact detail"),
    FORBIDDEN_PROTOTYPE_VERSION_ACCESS(40301, "Forbidden prototype version access", "Prototype version"),

    DEMO_WORKFLOW_ALREADY_RUNNING(40901, "Demo workflow is already running", "Demo stream duplicate submission"),
    IDEMPOTENCY_KEY_INVALID(40005, "Idempotency-Key is required and must use 1-128 URL-safe characters", "Async workflow submit"),
    IDEMPOTENCY_KEY_CONFLICT(40902, "Idempotency-Key was already used with a different request", "Async workflow submit"),
    WORKFLOW_RATE_LIMITED(42901, "Workflow submission rate limit exceeded", "Async workflow submit"),
    WORKFLOW_BACKPRESSURE(50301, "Workflow submission is temporarily unavailable due to backlog", "Async workflow submit"),
    WORKFLOW_RATE_LIMIT_UNAVAILABLE(50302, "Workflow submission is temporarily unavailable", "Async workflow submit"),
    PROTOTYPE_TUNING_INVALID(40006, "Prototype tuning parameters are invalid", "Prototype tuning"),
    PROTOTYPE_ARTIFACT_NOT_ELIGIBLE(40007, "Artifact is not eligible for a prototype version", "Prototype version creation"),

    USER_NOT_FOUND(40401, "User not found", "Current user"),
    PROJECT_NOT_FOUND(40401, "Project not found", "Project or agent run"),
    AGENT_RUN_NOT_FOUND(40401, "Agent run not found", "Agent run detail"),
    ARTIFACT_NOT_FOUND(40401, "Artifact not found", "Artifact detail"),
    WORKFLOW_RUN_NOT_FOUND(40401, "Workflow run not found", "Workflow detail"),
    PROTOTYPE_VERSION_NOT_FOUND(40401, "Prototype version not found", "Prototype version"),
    PROMPT_TEMPLATE_NOT_FOUND(40401, "Prompt template not found", "Prompt template detail"),
    ACTIVE_PROMPT_TEMPLATE_NOT_FOUND(40402, "Active prompt template not found, please configure a prompt template first", "Agent run prompt template selection"),

    SYSTEM_ERROR(50000, "Internal server error", "Global fallback"),
    AGENT_RUN_ERROR(50001, "Agent run failed", "Agent run"),
    PYTHON_BASE_URL_NOT_CONFIGURED(50002, "Python base URL is not configured", "Python client config"),

    PYTHON_CALL_FAILED(50201, "Failed to call Python service", "Python HTTP call"),
    PYTHON_EMPTY_RESPONSE(50202, "Python service returned empty response", "Python response"),
    PYTHON_INVALID_RESPONSE(50202, "Python service returned invalid response", "Python response format"),
    PYTHON_RESPONSE_PARSE_FAILED(50202, "Failed to parse Python response", "Python JSON parse"),
    PYTHON_RESPONSE_FAILED(50203, "Python service returned failure", "Python business response"),
    GAME_BUILD_FAILED(50003, "Failed to invoke game build service", "Game build");

    private final int code;
    private final String message;
    private final String scene;

    ErrorCode(int code, String message, String scene) {
        this.code = code;
        this.message = message;
        this.scene = scene;
    }
}
