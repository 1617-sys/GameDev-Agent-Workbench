package com.example.gameworkbench.evaluation;

import java.util.List;

public record SchemaEvaluationResult(String status, String schemaKey, String schemaVersion, List<String> violations) {
    public boolean passed() { return "PASSED".equals(status); }
}
