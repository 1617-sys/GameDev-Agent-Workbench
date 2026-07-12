package com.example.gameworkbench.evaluation;
public record RuleViolation(String code, String path, String severity, String expected, String actualSummary) {}
