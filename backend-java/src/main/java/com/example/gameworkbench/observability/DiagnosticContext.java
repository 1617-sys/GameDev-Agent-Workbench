package com.example.gameworkbench.observability;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded MDC scope for correlation identifiers. Never stores payloads or credentials. */
public final class DiagnosticContext implements AutoCloseable {
    public static final String TRACE_ID = "traceId";
    public static final String WORKFLOW_RUN_UUID = "workflowRunUuid";
    public static final String STEP_RUN_UUID = "stepRunUuid";
    public static final String AGENT_RUN_UUID = "agentRunUuid";
    public static final String MESSAGE_ID = "messageId";

    private final Map<String, String> previous = new LinkedHashMap<>();

    private DiagnosticContext(Map<String, String> values) {
        values.forEach((key, value) -> {
            previous.put(key, MDC.get(key));
            if (value == null || value.isBlank()) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

    public static DiagnosticContext open(String traceId, String workflowRunUuid, String messageId) {
        return new DiagnosticContext(Map.of(
                TRACE_ID, safe(traceId),
                WORKFLOW_RUN_UUID, safe(workflowRunUuid),
                MESSAGE_ID, safe(messageId)
        ));
    }

    public static DiagnosticContext trace(String traceId) {
        return new DiagnosticContext(Map.of(TRACE_ID, safe(traceId)));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void close() {
        previous.forEach((key, value) -> {
            if (value == null) MDC.remove(key); else MDC.put(key, value);
        });
    }
}
