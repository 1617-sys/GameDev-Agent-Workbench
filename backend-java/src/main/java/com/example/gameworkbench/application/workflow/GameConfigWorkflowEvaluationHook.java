package com.example.gameworkbench.application.workflow;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.GameConfigContractResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GameConfigWorkflowEvaluationHook implements WorkflowEvaluationHook {
    private final GameConfigContract contract;
    private final ObjectMapper objectMapper;

    public GameConfigWorkflowEvaluationHook(ObjectMapper objectMapper) {
        this(new GameConfigContract(objectMapper), objectMapper);
    }

    @Autowired
    public GameConfigWorkflowEvaluationHook(GameConfigContract contract, ObjectMapper objectMapper) {
        this.contract = contract;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(WorkflowStepPlan stepPlan) {
        return AgentType.GAME_CONFIG_GENERATE == stepPlan.agentType();
    }

    @Override
    public WorkflowEvaluationResult evaluate(WorkflowExecutionContext context, WorkflowStepPlan stepPlan,
            StepExecutionResult result) {
        String runSchema = context.workflowRun().getSchemaVersion();
        if (!GameConfigContract.RUN_SCHEMA_VERSION.equals(runSchema)
                && !GameConfigContract.LEGACY_RUN_SCHEMA_VERSION.equals(runSchema)) {
            return rejected(result.output().content(), "GameConfig validation failed: UNSUPPORTED_SCHEMA_VERSION at $");
        }
        GameConfigContractResult validation = contract.process(result.output().content());
        if (!validation.valid()) {
            return rejected(result.output().content(), "GameConfig validation failed: " + validation.violations().stream()
                    .map(value -> value.code() + " at " + value.path()).reduce((a, b) -> a + "; " + b).orElse("UNKNOWN"));
        }
        if (GameConfigContract.RUN_SCHEMA_VERSION.equals(runSchema) && validation.migrated()) {
            return rejected(result.output().content(), "GameConfig validation failed: LEGACY_WRITE_NOT_ALLOWED at $.version");
        }
        String briefMismatch = briefMismatch(context.inputSnapshot(), validation);
        if (briefMismatch != null) return rejected(result.output().content(), briefMismatch);
        return new WorkflowEvaluationResult(true, GameConfigContract.SCHEMA_KEY, GameConfigContract.SCHEMA_VERSION,
                contract.canonicalJson(validation.canonicalConfig()),
                validation.migrated() ? "GameConfig 1.0 migrated and validated as 2.0" : "GameConfig 2.0 contract validated");
    }

    private WorkflowEvaluationResult rejected(String rawContent, String summary) {
        return new WorkflowEvaluationResult(false, GameConfigContract.SCHEMA_KEY, GameConfigContract.SCHEMA_VERSION,
                rawContent, summary);
    }

    private String briefMismatch(String input, GameConfigContractResult validation) {
        try {
            var brief = objectMapper.readTree(input);
            if (!brief.isObject() || !brief.has("durationSeconds") || !brief.has("difficulty")) return null;
            var balance = validation.canonicalConfig().path("balance");
            if (brief.path("durationSeconds").asInt(-1) != balance.path("timeLimitSeconds").asInt(-2)) {
                return "GameConfig validation failed: BRIEF_DURATION_MISMATCH at $.balance.timeLimitSeconds";
            }
            if (!brief.path("difficulty").asText().equals(balance.path("difficulty").asText())) {
                return "GameConfig validation failed: BRIEF_DIFFICULTY_MISMATCH at $.balance.difficulty";
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
