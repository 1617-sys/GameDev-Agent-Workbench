package com.example.gameworkbench.evaluation;
import java.util.List;
public record RuleEvaluationResult(String status, String ruleVersion, List<RuleViolation> violations) {
 public boolean blockingPassed(){ return violations.stream().noneMatch(v -> "BLOCKING".equals(v.severity())); }
}
