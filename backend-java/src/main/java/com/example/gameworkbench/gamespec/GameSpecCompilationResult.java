package com.example.gameworkbench.gamespec;

import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record GameSpecCompilationResult(
        Status status,
        String sourceDigest,
        String runtimeIrDigest,
        ObjectNode canonicalSpec,
        ObjectNode runtimeIr,
        ObjectNode buildRequest,
        List<GameSpecDiagnostic> diagnostics
) {
    public enum Status { SUCCEEDED, FAILED }

    public GameSpecCompilationResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public static GameSpecCompilationResult failed(List<GameSpecDiagnostic> diagnostics) {
        return new GameSpecCompilationResult(Status.FAILED, null, null, null, null, null, diagnostics);
    }
}
