package com.example.gameworkbench.observability;

import com.example.gameworkbench.entity.ModelCallMetric;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationObservabilityTest {
    @Test
    void exposesBoundedWorkflowProviderAndSseMetricsWithoutUuidTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApplicationObservability observability = new ApplicationObservability(registry);

        observability.workflowEventPersisted("run.terminal", "SUCCESS");
        observability.workflowEventPersisted("run-uuid-12345678", "invented-status-12345678");
        ModelCallMetric metric = new ModelCallMetric();
        metric.setProvider("provider-account-12345678");
        metric.setMockState("TRUE");
        metric.setStatus("SUCCESS");
        metric.setLatencyMs(25L);
        observability.modelCallPersisted(metric);
        observability.workflowMessage("received");
        observability.workflowQueueLatency(Duration.ofMillis(12));
        observability.workflowExecution(Duration.ofMillis(8), "success");
        observability.workflowRetryOrDlq("retry", "credential-12345678");
        observability.evaluationPersisted("tenant-12345678", "invented-12345678");
        observability.ragRunPersisted("tenant-12345678", "tenant-12345678");
        observability.retrievalPersisted(false);
        observability.sseOpened();
        observability.sseClosed();

        assertThat(registry.get("gamedev.workflow.events").tag("event", "run.terminal").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.workflow.events").tag("event", "other").tag("status", "UNKNOWN").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.provider.calls").tag("provider", "other").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.provider.latency").tag("provider", "other").timer().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.workflow.messages").tag("outcome", "RECEIVED").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.workflow.queue.latency").timer().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.workflow.execution").tag("outcome", "SUCCESS").timer().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.workflow.failure.routes").tag("destination", "RETRY").tag("error", "UNKNOWN").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.evaluations").tag("evaluator", "UNKNOWN").tag("status", "UNKNOWN").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.rag.runs").tag("status", "UNKNOWN").tag("mock", "UNKNOWN").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.retrieval.selections").tag("mock", "FALSE").counter().count()).isEqualTo(1);
        assertThat(registry.get("gamedev.sse.connections.active").gauge().value()).isZero();
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).noneMatch(tag -> tag.getKey().toLowerCase().contains("uuid")
                        || tag.getKey().equalsIgnoreCase("traceId") || tag.getValue().contains("12345678")));
    }
}
