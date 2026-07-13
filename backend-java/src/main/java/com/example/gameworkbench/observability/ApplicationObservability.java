package com.example.gameworkbench.observability;

import com.example.gameworkbench.entity.ModelCallMetric;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Release metrics with deliberately bounded tags; correlation UUIDs belong in logs, never labels. */
@Component
public class ApplicationObservability {
    private static final Set<String> WORKFLOW_EVENTS = Set.of(
            "run.created", "run.status-changed", "run.terminal", "run.recovered", "run.retry-requested",
            "step.status-changed", "artifact.available"
    );
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "QUEUED", "RUNNING", "RETRY_WAIT", "SUCCESS", "FAILED", "TIMEOUT", "CANCELED", "UNKNOWN"
    );
    private static final Set<String> PROVIDERS = Set.of("mock", "fixture", "openai-compatible", "unknown");
    private static final Set<String> MOCK_STATES = Set.of("TRUE", "FALSE", "UNKNOWN");
    private static final Set<String> MESSAGE_OUTCOMES = Set.of("RECEIVED", "DUPLICATE", "REDELIVERED", "ACKED", "RETRY", "DLQ");
    private static final Set<String> EXECUTION_OUTCOMES = Set.of("SUCCESS", "FAILED");
    private static final Set<String> ERROR_CLASSES = Set.of(
            "INVALID_REQUEST", "PROMPT_CONFIGURATION", "OUTPUT_VALIDATION", "PROVIDER_RATE_LIMIT",
            "NETWORK_TIMEOUT", "INFRASTRUCTURE", "UNKNOWN"
    );
    private static final Set<String> EVALUATOR_TYPES = Set.of("SCHEMA", "RULE", "RUNTIME", "UNKNOWN");
    private static final Set<String> EVALUATION_STATUSES = Set.of("PASSED", "FAILED", "ERROR", "SKIPPED", "UNKNOWN");
    private static final Set<String> RAG_STATUSES = Set.of("PENDING", "DISABLED", "EMPTY", "AVAILABLE", "UNAVAILABLE", "UNKNOWN");
    private final MeterRegistry registry;
    private final AtomicInteger activeSse = new AtomicInteger();

    public ApplicationObservability(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("gamedev.sse.connections.active", activeSse, AtomicInteger::get)
                .description("Current active workflow SSE subscriptions")
                .baseUnit("connections")
                .register(registry);
    }

    public void workflowEventPersisted(String eventType, String status) {
        Counter.builder("gamedev.workflow.events")
                .description("Unique persisted workflow events; duplicate event keys are not counted")
                .tag("event", bounded(eventType, WORKFLOW_EVENTS, "other"))
                .tag("status", bounded(upper(status), STATUSES, "UNKNOWN"))
                .register(registry).increment();
    }

    public void workflowMessage(String outcome) {
        Counter.builder("gamedev.workflow.messages")
                .description("Workflow message handling outcomes at the consumer boundary")
                .tag("outcome", bounded(upper(outcome), MESSAGE_OUTCOMES, "UNKNOWN"))
                .register(registry).increment();
    }

    public void workflowQueueLatency(Duration duration) {
        if (duration == null || duration.isNegative()) return;
        Timer.builder("gamedev.workflow.queue.latency")
                .description("Time from persisted run creation to a successful execution claim")
                .register(registry).record(duration);
    }

    public void workflowExecution(Duration duration, String outcome) {
        if (duration == null || duration.isNegative()) return;
        Timer.builder("gamedev.workflow.execution")
                .description("Workflow runner invocation duration")
                .tag("outcome", bounded(upper(outcome), EXECUTION_OUTCOMES, "FAILED"))
                .register(registry).record(duration);
    }

    public void workflowRetryOrDlq(String destination, String errorClass) {
        String boundedDestination = bounded(upper(destination), Set.of("RETRY", "DLQ"), "DLQ");
        Counter.builder("gamedev.workflow.failure.routes")
                .description("Successfully handed-off retry and dead-letter messages")
                .tag("destination", boundedDestination)
                .tag("error", bounded(upper(errorClass), ERROR_CLASSES, "UNKNOWN"))
                .register(registry).increment();
        workflowMessage(boundedDestination);
    }

    public void evaluationPersisted(String evaluatorType, String status) {
        Counter.builder("gamedev.evaluations")
                .description("Persisted evaluation reports")
                .tag("evaluator", bounded(upper(evaluatorType), EVALUATOR_TYPES, "UNKNOWN"))
                .tag("status", bounded(upper(status), EVALUATION_STATUSES, "UNKNOWN"))
                .register(registry).increment();
    }

    public void ragRunPersisted(String status, String mockState) {
        Counter.builder("gamedev.rag.runs")
                .description("Persisted AgentRun RAG outcomes")
                .tag("status", bounded(upper(status), RAG_STATUSES, "UNKNOWN"))
                .tag("mock", bounded(upper(mockState), MOCK_STATES, "UNKNOWN"))
                .register(registry).increment();
    }

    public void retrievalPersisted(boolean mock) {
        Counter.builder("gamedev.retrieval.selections")
                .description("Unique persisted retrieval selections")
                .tag("mock", mock ? "TRUE" : "FALSE")
                .register(registry).increment();
    }

    public void modelCallPersisted(ModelCallMetric metric) {
        String provider = bounded(lower(metric.getProvider()), PROVIDERS, "other");
        String mockState = bounded(upper(metric.getMockState()), MOCK_STATES, "UNKNOWN");
        String outcome = bounded(upper(metric.getStatus()), Set.of("SUCCESS", "FAILED", "TIMEOUT", "UNKNOWN"), "UNKNOWN");
        Counter.builder("gamedev.provider.calls")
                .description("Persisted provider call results")
                .tag("provider", provider).tag("mock", mockState).tag("outcome", outcome)
                .register(registry).increment();
        if (metric.getLatencyMs() != null && metric.getLatencyMs() >= 0) {
            Timer.builder("gamedev.provider.latency")
                    .description("Provider call latency recorded with the persisted model metric")
                    .tag("provider", provider).tag("mock", mockState).tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(registry).record(Duration.ofMillis(metric.getLatencyMs()));
        }
    }

    public void sseOpened() {
        activeSse.incrementAndGet();
        Counter.builder("gamedev.sse.connections")
                .description("Workflow SSE subscriptions opened")
                .tag("action", "opened").register(registry).increment();
    }

    public void sseClosed() {
        activeSse.updateAndGet(value -> Math.max(0, value - 1));
        Counter.builder("gamedev.sse.connections")
                .description("Workflow SSE subscriptions closed")
                .tag("action", "closed").register(registry).increment();
    }

    private String lower(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }

    private String upper(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.toUpperCase(Locale.ROOT);
    }

    private String bounded(String value, Set<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }
}
