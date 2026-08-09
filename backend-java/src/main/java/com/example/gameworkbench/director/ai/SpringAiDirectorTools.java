package com.example.gameworkbench.director.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import com.example.gameworkbench.director.tool.DirectorToolContext;
import com.example.gameworkbench.director.tool.DirectorToolDefinition;
import com.example.gameworkbench.director.tool.DirectorToolRegistry;
import com.example.gameworkbench.director.tool.ClosedJsonSchemaValidator;
import com.example.gameworkbench.director.tool.ToolCallRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public final class SpringAiDirectorTools {
    public static final String FINISH = "DIRECTOR_FINISH";
    public static final String REQUEST_APPROVAL = "DIRECTOR_REQUEST_APPROVAL";
    public static final String FAIL = "DIRECTOR_FAIL";
    public static final String USER_ID = "director.user-id";
    public static final String PROJECT_ID = "director.project-id";
    public static final String RUN_ID = "director.run-id";
    public static final String CALL_ID = "director.call-id";
    public static final String IDEMPOTENCY_KEY = "director.idempotency-key";

    private final DirectorToolRegistry registry;
    private final ObjectMapper json;
    private final ClosedJsonSchemaValidator schemas = new ClosedJsonSchemaValidator();

    public SpringAiDirectorTools(DirectorToolRegistry registry, ObjectMapper json) {
        this.registry = registry;
        this.json = json;
    }

    public List<ToolCallback> callbacks(JsonNode snapshot) {
        Map<String, DirectorToolDefinition> registered = registry.discover().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(DirectorToolDefinition::name, definition -> definition));
        List<ToolCallback> callbacks = new ArrayList<>();
        for (JsonNode allowed : snapshot.path("allowedTools")) {
            DirectorToolDefinition definition = registered.get(allowed.path("name").asText());
            if (definition == null || !definition.version().equals(allowed.path("version").asText())) continue;
            callbacks.add(new RegistryToolCallback(definition));
        }
        callbacks.add(control(FINISH, "Finish the Director run only when the goal and guardrails are satisfied.", finishSchema()));
        callbacks.add(control(REQUEST_APPROVAL, "Pause the Director run for human approval after a draft candidate exists.", approvalSchema()));
        callbacks.add(control(FAIL, "Fail the Director run when no safe action remains.", failSchema()));
        return List.copyOf(callbacks);
    }

    public DirectorToolDefinition registered(String name) {
        return registry.discover().stream().filter(definition -> definition.name().equals(name)).findFirst().orElse(null);
    }

    public void validate(String name, JsonNode arguments) {
        DirectorToolDefinition domain = registered(name);
        if (domain != null) {
            schemas.validate(arguments, domain.argumentSchema());
            return;
        }
        ObjectNode schema = switch (name) {
            case FINISH -> finishSchema();
            case REQUEST_APPROVAL -> approvalSchema();
            case FAIL -> failSchema();
            default -> throw new IllegalArgumentException("Unknown Director tool");
        };
        schemas.validate(arguments, schema);
    }

    private ToolCallback control(String name, String description, ObjectNode schema) {
        return new ToolCallback() {
            private final ToolDefinition definition = DefaultToolDefinition.builder().name(name)
                    .description(description).inputSchema(write(schema)).build();
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public String call(String input) { throw new IllegalStateException("Director control tools are interpreted by the durable worker"); }
        };
    }

    private final class RegistryToolCallback implements ToolCallback {
        private final DirectorToolDefinition source;
        private final ToolDefinition definition;

        private RegistryToolCallback(DirectorToolDefinition source) {
            this.source = source;
            this.definition = DefaultToolDefinition.builder().name(source.name())
                    .description(description(source))
                    .inputSchema(write(source.argumentSchema())).build();
        }

        @Override public ToolDefinition getToolDefinition() { return definition; }

        @Override public String call(String input) {
            throw new IllegalStateException("Trusted Director context is required");
        }

        @Override public String call(String input, ToolContext toolContext) {
            Map<String, Object> context = toolContext.getContext();
            long userId = number(context, USER_ID);
            long projectId = number(context, PROJECT_ID);
            String runId = required(context, RUN_ID);
            String callId = required(context, CALL_ID);
            String idempotencyKey = required(context, IDEMPOTENCY_KEY);
            try {
                JsonNode arguments = json.readTree(input);
                return write(json.valueToTree(registry.execute(
                        new DirectorToolContext(userId, projectId, runId, callId),
                        new ToolCallRequest(callId, source.name(), source.version(), idempotencyKey, arguments, false))));
            } catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalArgumentException("Invalid Director tool input", exception); }
        }
    }

    private String description(DirectorToolDefinition source) {
        String purpose = switch (source.name()) {
            case "GET_GAMESPEC_CAPABILITIES" -> "Read the closed GameSpec archetype, entity, movement, presentation, runtime, and build capabilities.";
            case "COMPILE_GAME_SPEC" -> "Compile one GameSpec with the authoritative Java compiler and return stable diagnostics or canonical digests.";
            case "GET_PROTOTYPE_VERSION" -> "Read one existing prototype version before proposing changes or experiments.";
            case "COMPARE_PROTOTYPE_CONFIGS" -> "Read the bounded configuration differences between two existing prototype versions.";
            case "GET_PLAYER_RUN_STATUS" -> "Read the status and evidence references of one existing Player run.";
            case "GET_MACHINE_EPISODE_METRICS" -> "Read persisted metrics for one machine-play Episode.";
            case "CREATE_DRAFT_VERSION" -> "Create an immutable DRAFT candidate from an existing parent version; never publishes it.";
            case "GENERATE_NEIGHBOR_CANDIDATES" -> "Generate bounded deterministic parameter neighbors for an experiment.";
            case "RUN_PLAYER_EXPERIMENT" -> "Start a baseline-versus-candidate Persona and seed matrix; the durable workflow waits for completion.";
            case "GET_EXPERIMENT_STATUS" -> "Read the durable status of a previously started Player experiment.";
            case "COMPARE_CANDIDATE_METRICS" -> "Compare persisted baseline and candidate episodes against explicit acceptance thresholds.";
            case "REQUEST_HUMAN_APPROVAL" -> "Create an approval reference for an existing DRAFT; cannot approve or publish it.";
            default -> "Invoke one allowlisted Director operation.";
        };
        return purpose + " Tool=" + source.name() + "@" + source.version()
                + ", permission=" + source.permission() + ", risk=" + source.riskLevel() + ".";
    }

    private ObjectNode finishSchema() {
        ObjectNode schema = objectSchema();
        schema.withObject("/properties").set("summary", string(1, 1000));
        schema.withObject("/properties").set("consumedToolResultDigests", stringArray());
        schema.withArray("/required").add("summary");
        return schema;
    }

    private ObjectNode approvalSchema() {
        ObjectNode schema = objectSchema();
        schema.withObject("/properties").set("approvalRef", string(1, 255));
        schema.withObject("/properties").set("prototypeVersionUuid", string(1, 128));
        schema.withObject("/properties").set("evidenceResultRefs", stringArray());
        return schema;
    }

    private ObjectNode failSchema() {
        ObjectNode schema = objectSchema();
        schema.withObject("/properties").set("code", string(1, 80));
        schema.withObject("/properties").set("retryable", json.createObjectNode().put("type", "boolean"));
        schema.withArray("/required").add("code").add("retryable");
        return schema;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putObject("properties");
        schema.putArray("required");
        return schema;
    }

    private ObjectNode string(int min, int max) {
        return json.createObjectNode().put("type", "string").put("minLength", min).put("maxLength", max);
    }

    private ObjectNode stringArray() {
        ObjectNode array = json.createObjectNode().put("type", "array");
        array.set("items", string(1, 255));
        return array;
    }

    private long number(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("Missing trusted tool context: " + key);
    }

    private String required(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("Missing trusted tool context: " + key);
        return String.valueOf(value);
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
