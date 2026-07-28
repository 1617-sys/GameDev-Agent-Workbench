package com.example.gameworkbench.gameconfig;

import java.util.List;

import com.example.gameworkbench.evaluation.RuleViolation;

public record ResourceManifestContractResult(boolean valid, String canonicalContent, List<RuleViolation> violations) {
}
