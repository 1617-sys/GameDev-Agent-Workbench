package com.example.gameworkbench.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class SchemaEvaluator {
    private final ObjectMapper mapper;
    public SchemaEvaluator(ObjectMapper mapper) { this.mapper = mapper; }
    public SchemaEvaluationResult evaluate(String content, String schemaKey, String schemaVersion) {
        if (!"game-config".equals(schemaKey) || !"1.0".equals(schemaVersion)) return new SchemaEvaluationResult("SKIPPED", schemaKey, schemaVersion, List.of("SCHEMA_NOT_CONFIGURED"));
        List<String> violations = new ArrayList<>();
        try {
            JsonNode node = mapper.readTree(content);
            while (node != null && node.isObject() && (node.has("output") || node.has("data") || node.has("game_config") || node.has("gameConfig")))
                node = node.has("game_config") ? node.get("game_config") : node.has("gameConfig") ? node.get("gameConfig") : node.has("output") ? node.get("output") : node.get("data");
            if (node == null || !node.isObject()) violations.add("JSON_OBJECT_REQUIRED");
            else {
                required(node, "version", violations); required(node, "title", violations); required(node, "gameType", violations);
                requiredObject(node, "world", violations); requiredObject(node, "player", violations); requiredObject(node, "exit", violations);
                if (!(node.has("items") && node.get("items").isArray()) && !(node.has("collectibles") && node.get("collectibles").isArray())) violations.add("ITEMS_ARRAY_REQUIRED");
                if (!node.has("enemies") || !node.get("enemies").isArray()) violations.add("ENEMIES_ARRAY_REQUIRED");
            }
        } catch (Exception ignored) { violations.add("INVALID_JSON"); }
        return new SchemaEvaluationResult(violations.isEmpty() ? "PASSED" : "FAILED", schemaKey, schemaVersion, List.copyOf(violations));
    }
    private void required(JsonNode node, String name, List<String> errors) { if (!node.has(name) || !node.get(name).isTextual() || node.get(name).asText().isBlank()) errors.add(name.toUpperCase() + "_REQUIRED"); }
    private void requiredObject(JsonNode node, String name, List<String> errors) { if (!node.has(name) || !node.get(name).isObject()) errors.add(name.toUpperCase() + "_OBJECT_REQUIRED"); }
}
