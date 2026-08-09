package com.example.gameworkbench.gamespec;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecAuthorService {
    private static final int MAX_ATTEMPTS = 3;
    private final GameSpecApplicationService gameSpecs;
    private final SpecAuthorModel model;
    private final ObjectMapper json;

    public SpecAuthorResult author(Long userId, String projectUuid, String idea, ObjectNode initialSpec) {
        // Compile once up front to enforce project ownership before any paid model call.
        GameSpecCompilationResult compilation = gameSpecs.compile(userId, projectUuid,
                initialSpec == null ? json.createObjectNode() : initialSpec);
        ObjectNode candidate = initialSpec;
        List<SpecAuthorAttempt> attempts = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            SpecAuthorModelResponse authored = model.author(new SpecAuthorModelRequest(
                    userId, projectUuid, idea, candidate, diagnostics(compilation), attempt));
            candidate = authored.spec();
            compilation = gameSpecs.compile(userId, projectUuid, candidate);
            boolean accepted = compilation.status() == GameSpecCompilationResult.Status.SUCCEEDED;
            attempts.add(new SpecAuthorAttempt(attempt, candidate.deepCopy(), compilation.diagnostics(), accepted,
                    authored.modelEvidence() == null ? json.createObjectNode() : authored.modelEvidence().deepCopy()));
            if (accepted) return new SpecAuthorResult("SUCCEEDED", compilation.canonicalSpec(), compilation, List.copyOf(attempts));
        }
        return new SpecAuthorResult("FAILED", candidate, compilation, List.copyOf(attempts));
    }

    private String diagnostics(GameSpecCompilationResult compilation) {
        if (compilation == null || compilation.diagnostics().isEmpty()) return "none";
        try { return json.writeValueAsString(compilation.diagnostics()); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
