package com.example.gameworkbench.director.tool;

import java.util.List;
import com.example.gameworkbench.gamespec.ArcadeCollectCapabilityRegistry;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class GameSpecDirectorTools {
    private GameSpecDirectorTools() {}

    public static List<DirectorTool> create(ArcadeCollectCapabilityRegistry capabilities,
            GameSpecCompiler compiler, ObjectMapper json) {
        return List.of(
                new SimpleTool(definition(json, "GET_GAMESPEC_CAPABILITIES", emptySchema(json), ToolPermission.READ),
                        (context, arguments) -> capabilities.snapshot()),
                new SimpleTool(definition(json, "COMPILE_GAME_SPEC", compileSchema(json), ToolPermission.WRITE),
                        (context, arguments) -> json.valueToTree(compiler.compile(json.readTree(arguments.path("specJson").asText()))))
        );
    }

    private static DirectorToolDefinition definition(ObjectMapper json, String name, JsonNode schema, ToolPermission permission) {
        return new DirectorToolDefinition(name, "1", schema, permission,
                permission == ToolPermission.READ ? ToolRiskLevel.LOW : ToolRiskLevel.MEDIUM,
                5_000, 2_048, true);
    }

    private static ObjectNode emptySchema(ObjectMapper json) {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode compileSchema(ObjectMapper json) {
        ObjectNode schema = emptySchema(json);
        ObjectNode spec = schema.withObject("/properties").putObject("specJson");
        spec.put("type", "string");
        spec.put("minLength", 2);
        spec.put("maxLength", 65_535);
        schema.withArray("/required").add("specJson");
        return schema;
    }

    @FunctionalInterface
    private interface Execution { JsonNode execute(DirectorToolContext context, JsonNode arguments) throws Exception; }

    private record SimpleTool(DirectorToolDefinition definition, Execution execution) implements DirectorTool {
        @Override public JsonNode execute(DirectorToolContext context, JsonNode arguments) throws Exception {
            return execution.execute(context, arguments);
        }
    }
}
