package com.example.gameworkbench.gameconfig;

import java.util.List;

import com.example.gameworkbench.evaluation.RuleViolation;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record GameConfigContractResult(ObjectNode canonicalConfig, List<RuleViolation> violations, boolean migrated) {
    public boolean valid() {
        return canonicalConfig != null && violations.stream().noneMatch(v -> "BLOCKING".equals(v.severity()));
    }
}
