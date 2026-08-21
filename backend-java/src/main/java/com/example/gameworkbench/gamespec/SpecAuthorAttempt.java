package com.example.gameworkbench.gamespec;

import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record SpecAuthorAttempt(int attempt, ObjectNode spec, List<GameSpecDiagnostic> diagnostics,
        boolean accepted, ObjectNode modelEvidence) {}
