package com.example.gameworkbench.application.workflow;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.common.enums.AgentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Mirrors the documented front-end GameConfig contract before optional defaults are applied. */
@Component
public class GameConfigWorkflowEvaluationHook implements WorkflowEvaluationHook {
    private static final String SCHEMA_KEY = "game-config";
    private static final String RUN_SCHEMA_VERSION = "game-config/1.0";
    private final ObjectMapper objectMapper;

    public GameConfigWorkflowEvaluationHook(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override public boolean supports(WorkflowStepPlan stepPlan) {
        return AgentType.GAME_CONFIG_GENERATE == stepPlan.agentType();
    }

    @Override public WorkflowEvaluationResult evaluate(WorkflowExecutionContext context, WorkflowStepPlan stepPlan,
            StepExecutionResult result) {
        List<String> errors = new ArrayList<>();
        ObjectNode config = extract(result.output().content(), errors);
        if (config != null) validate(config, errors);
        if (!errors.isEmpty()) throw new WorkflowEvaluationException("GameConfig validation failed: " + String.join("; ", errors));
        if (!RUN_SCHEMA_VERSION.equals(context.workflowRun().getSchemaVersion())) {
            throw new WorkflowEvaluationException("GameConfig validation failed: unsupported frozen schema version");
        }
        try {
            return new WorkflowEvaluationResult(true, SCHEMA_KEY, "1.0", objectMapper.writeValueAsString(config),
                    "GameConfig contract validated");
        } catch (Exception exception) {
            throw new WorkflowEvaluationException("GameConfig validation failed: serialization error");
        }
    }

    private ObjectNode extract(String raw, List<String> errors) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            for (int depth = 0; depth < 4 && node != null && node.isObject(); depth++) {
                if (node.has("game_config")) node = node.get("game_config");
                else if (node.has("gameConfig")) node = node.get("gameConfig");
                else if (node.has("data")) node = node.get("data");
                else if (node.path("raw_result").has("game_config")) node = node.path("raw_result").get("game_config");
                else if (node.path("rawResult").has("gameConfig")) node = node.path("rawResult").get("gameConfig");
                else break;
            }
            if (node != null && node.isObject() && (node.has("version") || node.has("world") || node.has("player") || node.has("rules"))) {
                return ((ObjectNode) node).deepCopy();
            }
        } catch (Exception ignored) { }
        errors.add("GameConfig must be a JSON object with required runtime fields");
        return null;
    }

    private void validate(ObjectNode config, List<String> errors) {
        requiredText(config, "version", errors); requiredText(config, "title", errors);
        String gameType = text(config, "gameType", "game_type");
        if (gameType == null) errors.add("missing gameType");
        else if (!"top_down_collect".equals(gameType)) errors.add("unsupported gameType");
        else config.put("gameType", gameType);
        ObjectNode world = object(config, "world", errors); ObjectNode player = object(config, "player", errors);
        JsonNode items = field(config, "items", "collectibles"); if (items == null || !items.isArray()) errors.add("items must be an array"); else config.set("items", items);
        if (!config.path("enemies").isArray()) errors.add("enemies must be an array");
        ObjectNode exit = object(config, "exit", errors); object(config, "rules", errors); object(config, "ui", errors);
        coordinates(world, "world.width", "width", "world.height", "height", errors);
        coordinates(player, "player.x", "x", "player.y", "y", errors);
        coordinates(exit, "exit.x", "x", "exit.y", "y", errors);
    }

    private ObjectNode object(ObjectNode config, String field, List<String> errors) {
        JsonNode node = config.get(field);
        if (node == null || !node.isObject() || node.isArray()) { errors.add("missing " + field); return null; }
        return (ObjectNode) node;
    }
    private void requiredText(ObjectNode config, String field, List<String> errors) {
        if (text(config, field, null) == null) errors.add("missing " + field);
    }
    private String text(ObjectNode config, String primary, String alias) {
        JsonNode node = field(config, primary, alias);
        return node == null || !node.isTextual() || node.asText().isBlank() ? null : node.asText();
    }
    private JsonNode field(ObjectNode config, String primary, String alias) {
        return config.has(primary) ? config.get(primary) : alias != null && config.has(alias) ? config.get(alias) : null;
    }
    private void coordinates(ObjectNode object, String firstLabel, String first, String secondLabel, String second, List<String> errors) {
        if (object == null) return;
        if (!number(object.get(first)) || !number(object.get(second))) errors.add(firstLabel + " / " + secondLabel + " must be numbers");
    }
    private boolean number(JsonNode value) {
        if (value == null || value.isContainerNode()) return false;
        try { return Double.isFinite(Double.parseDouble(value.asText())); } catch (Exception ignored) { return false; }
    }
}
