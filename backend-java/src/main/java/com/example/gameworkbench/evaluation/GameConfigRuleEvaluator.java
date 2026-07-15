package com.example.gameworkbench.evaluation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.GameConfigContractResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GameConfigRuleEvaluator {
    private final GameConfigContract contract;
    private final RuntimeCapabilityRegistry capabilities;

    public GameConfigRuleEvaluator(ObjectMapper mapper, RuntimeCapabilityRegistry capabilities) {
        this.contract = new GameConfigContract(mapper);
        this.capabilities = capabilities;
    }

    public RuleEvaluationResult evaluate(String content) {
        GameConfigContractResult result = contract.process(content);
        List<RuleViolation> violations = result.violations();
        if (result.valid() && !capabilities.supportsGameType(result.canonicalConfig().path("metadata").path("gameType").asText())) {
            violations = List.of(new RuleViolation("UNSUPPORTED_GAME_TYPE", "$.metadata.gameType", "BLOCKING",
                    GameConfigContract.GAME_TYPE, result.canonicalConfig().path("metadata").path("gameType").asText()));
        }
        return new RuleEvaluationResult(violations.isEmpty() ? "PASSED" : "FAILED", capabilities.version(), violations);
    }
}
