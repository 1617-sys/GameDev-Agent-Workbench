package com.example.gameworkbench.evaluation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.GameConfigContractResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SchemaEvaluator {
    private final GameConfigContract contract;

    public SchemaEvaluator(ObjectMapper mapper) {
        this.contract = new GameConfigContract(mapper);
    }

    public SchemaEvaluationResult evaluate(String content, String schemaKey, String schemaVersion) {
        if (!GameConfigContract.SCHEMA_KEY.equals(schemaKey)
                || !("1.0".equals(schemaVersion) || GameConfigContract.SCHEMA_VERSION.equals(schemaVersion))) {
            return new SchemaEvaluationResult("SKIPPED", schemaKey, schemaVersion, List.of("SCHEMA_NOT_CONFIGURED"));
        }
        GameConfigContractResult result = contract.process(content);
        List<String> violations = result.violations().stream()
                .map(value -> value.code() + "@" + value.path()).toList();
        return new SchemaEvaluationResult(result.valid() ? "PASSED" : "FAILED", schemaKey, schemaVersion, violations);
    }
}
