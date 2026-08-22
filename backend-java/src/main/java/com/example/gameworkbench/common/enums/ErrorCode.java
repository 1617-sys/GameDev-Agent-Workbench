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
    FORBIDDEN_PLAYTEST_ACCESS(40301, "Forbidden playtest session access", "Playtest telemetry"),
    FORBIDDEN_EPISODE_ACCESS(40301, "Forbidden machine Episode access", "Machine Episode"),

    DEMO_WORKFLOW_ALREADY_RUNNING(40901, "Demo workflow is already running", "Demo stream duplicate submission"),
    IDEMPOTENCY_KEY_INVALID(40005, "Idempotency-Key is required and must use 1-128 URL-safe characters", "Async workflow submit"),
    IDEMPOTENCY_KEY_CONFLICT(40902, "Idempotency-Key was already used with a different request", "Async workflow submit"),
    WORKFLOW_RATE_LIMITED(42901, "Workflow submission rate limit exceeded", "Async workflow submit"),
    WORKFLOW_BACKPRESSURE(50301, "Workflow submission is temporarily unavailable due to backlog", "Async workflow submit"),
    WORKFLOW_RATE_LIMIT_UNAVAILABLE(50302, "Workflow submission is temporarily unavailable", "Async workflow submit"),
    PROTOTYPE_TUNING_INVALID(40006, "Prototype tuning parameters are invalid", "Prototype tuning"),
    PROTOTYPE_ARTIFACT_NOT_ELIGIBLE(40007, "Artifact is not eligible for a prototype version", "Prototype version creation"),
    TELEMETRY_INVALID(40008, "Telemetry event violates the frozen contract", "Playtest telemetry"),
    TELEMETRY_TOO_LARGE(41301, "Telemetry batch exceeds the 64 KiB limit", "Playtest telemetry"),
    TELEMETRY_RATE_LIMITED(42902, "Telemetry rate limit exceeded", "Playtest telemetry"),
    TELEMETRY_IDEMPOTENCY_CONFLICT(40903, "Telemetry replay conflicts with persisted facts", "Playtest telemetry"),
    PLAYTEST_SESSION_CLOSED(40904, "Playtest session no longer accepts events", "Playtest telemetry"),
    PLAYTEST_SAMPLE_INSUFFICIENT(40905, "At least five ended sessions are required", "Balance evaluation"),
    EXPORT_INPUT_INCOMPLETE(40906, "Prototype export inputs are incomplete", "Prototype export"),
    EXPORT_NOT_READY(40907, "Prototype export is not ready for download", "Prototype export"),
    EXPORT_RETRY_EXHAUSTED(40908, "Prototype export retry limit is exhausted", "Prototype export"),
    EXPORT_SECURITY_REJECTED(40009, "Prototype export failed security validation", "Prototype export"),
    EPISODE_INVALID(40010, "Machine Episode result violates the frozen contract", "Machine Episode"),
    EPISODE_BINDING_MISMATCH(40909, "PrototypeVersion and config digest do not match", "Machine Episode"),
    EPISODE_IDEMPOTENCY_CONFLICT(40910, "Episode idempotency key conflicts with persisted results", "Machine Episode"),
    PLAYER_RUN_INVALID(40011, "Player Run request is not registered or exceeds its budget", "Player Run"),
    PLAYER_RUN_IDEMPOTENCY_CONFLICT(40911, "Player Run idempotency key conflicts with another request", "Player Run"),
    DIRECTOR_RUN_INVALID(40012, "Director Run request or transition is invalid", "Director Run"),
    DIRECTOR_RUN_IDEMPOTENCY_CONFLICT(40912, "Director Run idempotency key conflicts with another request", "Director Run"),
    DIRECTOR_RUN_CONCURRENT_UPDATE(40913, "Director Run was concurrently updated", "Director Run"),
    DIRECTOR_TOOL_INVALID(40013, "Director tool request violates its registered schema", "Director Tool"),
    DIRECTOR_TOOL_FORBIDDEN(40302, "Director tool cannot access the requested project resource", "Director Tool"),
    DIRECTOR_TOOL_TIMEOUT(50401, "Director tool execution timed out", "Director Tool"),
    PROTOTYPE_APPROVAL_CONFLICT(40914, "Prototype approval conflicts with its lifecycle or prior decision", "Prototype Approval"),
    CANDIDATE_GENERATION_INVALID(40014, "Candidate generation request has no bounded valid plan", "Experiment Candidate"),
    GAMESPEC_INVALID(40015, "GameSpec does not satisfy the registered contract", "GameSpec compilation"),
    GENERATION_RUN_IDEMPOTENCY_CONFLICT(40916, "Generation Run idempotency key conflicts with another request", "Generation Run"),
    GENERATION_RUN_CONCURRENT_UPDATE(40917, "Generation Run was concurrently updated or is already claimed", "Generation Run"),
    GENERATION_APPROVAL_CONFLICT(40918, "Generation approval conflicts with its lifecycle or prior decision", "Generation Run Approval"),
    GENERATION_RELEASE_FORBIDDEN(40919, "Generation Run must be approved before release", "Generation Run Release"),
    COCOS_BUILD_UNAVAILABLE(50303, "Cocos Build Worker is not configured", "Cocos build"),
    AI_MODEL_UNAVAILABLE(50304, "AI model provider is unavailable or not configured", "Spring AI model call"),
    AI_MODEL_INVALID_RESPONSE(50204, "AI model returned an invalid structured response", "Spring AI model call"),
    EXPERIMENT_NOT_COMPARABLE(40915, "Experiment samples cannot be compared", "Director Experiment"),

    USER_NOT_FOUND(40401, "User not found", "Current user"),
    PROJECT_NOT_FOUND(40401, "Project not found", "Project or agent run"),
    AGENT_RUN_NOT_FOUND(40401, "Agent run not found", "Agent run detail"),
    ARTIFACT_NOT_FOUND(40401, "Artifact not found", "Artifact detail"),
    WORKFLOW_RUN_NOT_FOUND(40401, "Workflow run not found", "Workflow detail"),
    PROTOTYPE_VERSION_NOT_FOUND(40401, "Prototype version not found", "Prototype version"),
    PLAYTEST_SESSION_NOT_FOUND(40401, "Playtest session not found", "Playtest telemetry"),
    PROTOTYPE_EXPORT_NOT_FOUND(40401, "Prototype export not found", "Prototype export"),
    EPISODE_NOT_FOUND(40401, "Machine Episode or batch not found", "Machine Episode"),
    PLAYER_RUN_NOT_FOUND(40401, "Player Run not found", "Player Run"),
    DIRECTOR_RUN_NOT_FOUND(40401, "Director Run not found", "Director Run"),
    DIRECTOR_TOOL_NOT_FOUND(40401, "Director tool or version is not registered", "Director Tool"),
    GENERATION_RUN_NOT_FOUND(40401, "Generation Run not found", "Generation Run"),
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
