package com.example.gameworkbench.gamespec;

import java.util.List;

public record GameSpecDiagnostic(
        String code,
        Severity severity,
        String path,
        String message,
        List<String> allowedValues,
        boolean retryableBySpecChange
) {
    public enum Severity { ERROR, WARNING }

    public GameSpecDiagnostic {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
