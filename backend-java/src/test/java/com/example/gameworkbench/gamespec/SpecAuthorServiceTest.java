package com.example.gameworkbench.gamespec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SpecAuthorServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void compilerDiagnosticsDriveABoundedRepairLoop() {
        GameSpecApplicationService compiler = mock(GameSpecApplicationService.class);
        var diagnostic = new GameSpecDiagnostic("UNKNOWN_FIELD", GameSpecDiagnostic.Severity.ERROR,
                "/invented", "field is not registered", List.of(), true);
        when(compiler.compile(eq(7L), eq("project"), any()))
                .thenReturn(GameSpecCompilationResult.failed(List.of(diagnostic)))
                .thenReturn(GameSpecCompilationResult.failed(List.of(diagnostic)))
                .thenReturn(success());
        AtomicInteger calls = new AtomicInteger();
        List<String> suppliedDiagnostics = new ArrayList<>();
        SpecAuthorModel model = request -> {
            suppliedDiagnostics.add(request.diagnostics());
            return new SpecAuthorModelResponse(json.createObjectNode()
                    .put("attempt", calls.incrementAndGet()), json.createObjectNode().put("attempt", request.attempt()));
        };

        SpecAuthorResult result = new SpecAuthorService(compiler, model, json)
                .author(7L, "project", "make a bounded collect game", null);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.attempts()).hasSize(2);
        assertThat(result.attempts().get(0).accepted()).isFalse();
        assertThat(result.attempts().get(1).accepted()).isTrue();
        assertThat(suppliedDiagnostics).allMatch(value -> value.contains("UNKNOWN_FIELD"));
        assertThat(result.attempts().get(1).modelEvidence().path("attempt").asInt()).isEqualTo(2);
    }

    @Test
    void stopsAfterThreeInvalidModelResults() {
        GameSpecApplicationService compiler = mock(GameSpecApplicationService.class);
        when(compiler.compile(eq(7L), eq("project"), any()))
                .thenReturn(GameSpecCompilationResult.failed(List.of()));
        SpecAuthorModel model = request -> new SpecAuthorModelResponse(
                json.createObjectNode().put("invalid", true), json.createObjectNode());

        SpecAuthorResult result = new SpecAuthorService(compiler, model, json)
                .author(7L, "project", "make a bounded collect game", null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.attempts()).hasSize(3);
    }

    private GameSpecCompilationResult success() {
        ObjectNode spec = json.createObjectNode().put("specVersion", "0.1");
        return new GameSpecCompilationResult(GameSpecCompilationResult.Status.SUCCEEDED, "source", "runtime",
                spec, json.createObjectNode(), json.createObjectNode(), List.of());
    }
}
