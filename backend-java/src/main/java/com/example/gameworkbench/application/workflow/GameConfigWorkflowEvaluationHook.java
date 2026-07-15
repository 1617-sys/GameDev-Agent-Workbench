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

    public GameConfigWorkflowEvaluationHook(ObjectMapper objectMapper) {
        this(new GameConfigContract(objectMapper));
    }

    @Autowired
    public GameConfigWorkflowEvaluationHook(GameConfigContract contract) {
        this.contract = contract;
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
            throw new WorkflowEvaluationException("GameConfig validation failed: UNSUPPORTED_SCHEMA_VERSION at $");
        }
        GameConfigContractResult validation = contract.process(result.output().content());
        if (!validation.valid()) {
            throw new WorkflowEvaluationException("GameConfig validation failed: " + validation.violations().stream()
                    .map(value -> value.code() + " at " + value.path()).reduce((a, b) -> a + "; " + b).orElse("UNKNOWN"));
        }
        if (GameConfigContract.RUN_SCHEMA_VERSION.equals(runSchema) && validation.migrated()) {
            throw new WorkflowEvaluationException("GameConfig validation failed: LEGACY_WRITE_NOT_ALLOWED at $.version");
        }
        return new WorkflowEvaluationResult(true, GameConfigContract.SCHEMA_KEY, GameConfigContract.SCHEMA_VERSION,
                contract.canonicalJson(validation.canonicalConfig()),
                validation.migrated() ? "GameConfig 1.0 migrated and validated as 2.0" : "GameConfig 2.0 contract validated");
    }
}
