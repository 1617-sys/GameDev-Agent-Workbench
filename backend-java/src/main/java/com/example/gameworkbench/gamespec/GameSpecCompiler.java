package com.example.gameworkbench.gamespec;

import static com.example.gameworkbench.gamespec.GameSpecDiagnostic.Severity.ERROR;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public final class GameSpecCompiler {
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "specVersion", "archetype", "metadata", "world", "player", "entities", "rules", "presentation");
    private static final Set<String> PROFILE_FIELDS = Set.of(
            "visualThemeId", "assetPackId", "animationProfileId", "cameraProfileId",
            "feedbackProfileId", "uiSkinId", "audioProfileId");

    private final ObjectMapper json;
    private final ArcadeCollectCapabilityRegistry registry;

    public GameSpecCompiler(ObjectMapper json, ArcadeCollectCapabilityRegistry registry) {
        this.json = json;
        this.registry = registry;
    }

    public GameSpecCompilationResult compile(JsonNode rawSpec) {
        List<GameSpecDiagnostic> diagnostics = new ArrayList<>();
        if (rawSpec == null || !rawSpec.isObject()) {
            error(diagnostics, "GS1000_JSON_OBJECT_REQUIRED", "", "GameSpec must be one JSON object");
            return GameSpecCompilationResult.failed(sorted(diagnostics));
        }
        ObjectNode spec = ((ObjectNode) rawSpec).deepCopy();
        validate(spec, diagnostics);
        if (!diagnostics.isEmpty()) return GameSpecCompilationResult.failed(sorted(diagnostics));

        ObjectNode canonical = sortObject(spec);
        String canonicalJson = write(canonical);
        String sourceDigest = digest(canonicalJson);
        ObjectNode runtimeIr = compileRuntimeIr(canonical, sourceDigest);
        String runtimeIrDigest = digest(write(sortObject(runtimeIr)));
        runtimeIr.put("runtimeIrDigest", runtimeIrDigest);
        ObjectNode buildRequest = buildRequest(canonical, sourceDigest, runtimeIrDigest);
        return new GameSpecCompilationResult(GameSpecCompilationResult.Status.SUCCEEDED, sourceDigest,
                runtimeIrDigest, canonical, sortObject(runtimeIr), sortObject(buildRequest), List.of());
    }

    private void validate(ObjectNode spec, List<GameSpecDiagnostic> errors) {
        unknown(spec, "", ROOT_FIELDS, errors);
        constant(spec, "specVersion", "", ArcadeCollectCapabilityRegistry.SPEC_VERSION, errors);
        constant(spec, "archetype", "", ArcadeCollectCapabilityRegistry.ARCHETYPE, errors);

        ObjectNode metadata = object(spec, "metadata", "", errors);
        if (metadata != null) {
            unknown(metadata, "/metadata", Set.of("title", "seed", "description"), errors);
            text(metadata, "title", "/metadata", 1, 80, errors);
            integer(metadata, "seed", "/metadata", 0, Integer.MAX_VALUE, errors);
            optionalText(metadata, "description", "/metadata", 500, errors);
        }

        ObjectNode world = object(spec, "world", "", errors);
        Integer width = null, height = null;
        if (world != null) {
            unknown(world, "/world", Set.of("width", "height", "timeLimitSeconds", "backgroundColor"), errors);
            width = integer(world, "width", "/world", 640, 1920, errors);
            height = integer(world, "height", "/world", 360, 1080, errors);
            integer(world, "timeLimitSeconds", "/world", 15, 600, errors);
            optionalText(world, "backgroundColor", "/world", 16, errors);
        }

        ObjectNode player = object(spec, "player", "", errors);
        if (player != null) {
            unknown(player, "/player", Set.of("movement", "speed", "health", "radius", "spawn"), errors);
            allowed(player, "movement", "/player", registry.movements(), errors);
            integer(player, "speed", "/player", 80, 500, errors);
            integer(player, "health", "/player", 1, 10, errors);
            integer(player, "radius", "/player", 12, 48, errors);
            ObjectNode spawn = object(player, "spawn", "/player", errors);
            validatePoint(spawn, "/player/spawn", width, height, errors);
        }

        ArrayNode entities = array(spec, "entities", "", 2, 64, errors);
        validateEntities(entities, width, height, errors);
        ArrayNode rules = array(spec, "rules", "", 0, 32, errors);
        validateRules(rules, errors);

        ObjectNode presentation = object(spec, "presentation", "", errors);
        if (presentation != null) {
            unknown(presentation, "/presentation", PROFILE_FIELDS, errors);
            for (String field : PROFILE_FIELDS) {
                allowed(presentation, field, "/presentation", registry.allowedProfileValues(field), errors);
            }
        }
    }

    private void validateEntities(ArrayNode entities, Integer width, Integer height, List<GameSpecDiagnostic> errors) {
        if (entities == null) return;
        Set<String> ids = new HashSet<>();
        int collectibles = 0, exits = 0;
        for (int i = 0; i < entities.size(); i++) {
            String path = "/entities/" + i;
            JsonNode raw = entities.get(i);
            if (!raw.isObject()) {
                error(errors, "GS1002_TYPE_MISMATCH", path, "entity must be an object");
                continue;
            }
            ObjectNode entity = (ObjectNode) raw;
            unknown(entity, path, Set.of("id", "type", "x", "y", "size", "score", "speed", "patrolAxis", "patrolRange"), errors);
            String id = text(entity, "id", path, 1, 32, errors);
            if (id != null && !ID.matcher(id).matches()) {
                error(errors, "GS1202_INVALID_ID", path + "/id", "id must match " + ID.pattern());
            } else if (id != null && !ids.add(id)) {
                error(errors, "GS1202_DUPLICATE_ID", path + "/id", "entity id must be unique");
            }
            String type = allowed(entity, "type", path, registry.entityTypes(), errors);
            Integer x = integer(entity, "x", path, 0, width == null ? 1920 : width, errors);
            Integer y = integer(entity, "y", path, 0, height == null ? 1080 : height, errors);
            integer(entity, "size", path, 12, 160, errors);
            if ("collectible".equals(type)) {
                collectibles++;
                integer(entity, "score", path, 1, 10_000, errors);
                forbidden(entity, path, Set.of("speed", "patrolAxis", "patrolRange"), errors);
            } else if ("enemy".equals(type)) {
                integer(entity, "speed", path, 20, 300, errors);
                allowed(entity, "patrolAxis", path, Set.of("x", "y"), errors);
                integer(entity, "patrolRange", path, 16, 600, errors);
                forbidden(entity, path, Set.of("score"), errors);
            } else {
                if ("exit".equals(type)) exits++;
                forbidden(entity, path, Set.of("score", "speed", "patrolAxis", "patrolRange"), errors);
            }
            if (x != null && y != null && width != null && height != null && (x == 0 || y == 0 || x == width || y == height)) {
                error(errors, "GS1301_VALUE_OUT_OF_RANGE", path, "entity center must be inside world bounds");
            }
        }
        if (collectibles == 0) error(errors, "GS1601_UNREACHABLE_WIN_CONDITION", "/entities", "at least one collectible is required");
        if (exits != 1) error(errors, "GS1601_UNREACHABLE_WIN_CONDITION", "/entities", "exactly one exit is required");
    }

    private void validateRules(ArrayNode rules, List<GameSpecDiagnostic> errors) {
        if (rules == null) return;
        for (int i = 0; i < rules.size(); i++) {
            String path = "/rules/" + i;
            JsonNode raw = rules.get(i);
            if (!raw.isObject()) {
                error(errors, "GS1002_TYPE_MISMATCH", path, "rule must be an object");
                continue;
            }
            ObjectNode rule = (ObjectNode) raw;
            unknown(rule, path, Set.of("when", "if", "then"), errors);
            allowed(rule, "when", path, Set.of("collectible.collected"), errors);
            ObjectNode condition = object(rule, "if", path, errors);
            if (condition != null) {
                unknown(condition, path + "/if", Set.of("counter", "equals"), errors);
                allowed(condition, "counter", path + "/if", Set.of("remainingCollectibles"), errors);
                integer(condition, "equals", path + "/if", 0, 0, errors);
            }
            ArrayNode actions = array(rule, "then", path, 1, 4, errors);
            if (actions != null) for (int j = 0; j < actions.size(); j++) {
                String actionPath = path + "/then/" + j;
                if (!actions.get(j).isObject()) {
                    error(errors, "GS1002_TYPE_MISMATCH", actionPath, "action must be an object");
                    continue;
                }
                ObjectNode action = (ObjectNode) actions.get(j);
                unknown(action, actionPath, Set.of("action"), errors);
                allowed(action, "action", actionPath, Set.of("exit.unlock"), errors);
            }
        }
    }

    private ObjectNode compileRuntimeIr(ObjectNode spec, String sourceDigest) {
        ObjectNode ir = json.createObjectNode();
        ir.put("irVersion", "cocos-runtime-ir/1");
        ir.put("sourceDigest", sourceDigest);
        ir.put("archetype", spec.path("archetype").asText());
        ir.set("metadata", spec.path("metadata").deepCopy());
        ir.set("world", spec.path("world").deepCopy());
        ir.set("player", spec.path("player").deepCopy());
        ir.set("entities", spec.path("entities").deepCopy());
        ir.set("rules", spec.path("rules").deepCopy());
        ir.set("presentation", spec.path("presentation").deepCopy());
        ir.put("capabilityRegistryVersion", ArcadeCollectCapabilityRegistry.VERSION);
        ir.put("runtimeShellVersion", ArcadeCollectCapabilityRegistry.RUNTIME_SHELL_VERSION);
        return sortObject(ir);
    }

    private ObjectNode buildRequest(ObjectNode spec, String sourceDigest, String runtimeIrDigest) {
        ObjectNode request = json.createObjectNode();
        request.put("requestVersion", "cocos-build-request/1");
        request.put("runtimeShellVersion", ArcadeCollectCapabilityRegistry.RUNTIME_SHELL_VERSION);
        request.put("cocosCreatorVersion", ArcadeCollectCapabilityRegistry.COCOS_VERSION);
        request.put("target", "web-mobile");
        request.put("gameSpecDigest", sourceDigest);
        request.put("runtimeIrDigest", runtimeIrDigest);
        request.put("capabilityRegistryVersion", ArcadeCollectCapabilityRegistry.VERSION);
        request.put("buildProfileVersion", ArcadeCollectCapabilityRegistry.BUILD_PROFILE_VERSION);
        ObjectNode profileDigests = request.putObject("profileDigests");
        spec.path("presentation").fields().forEachRemaining(entry ->
                profileDigests.put(entry.getKey(), digest(entry.getKey() + ":" + entry.getValue().asText())));
        return request;
    }

    private ObjectNode object(ObjectNode parent, String field, String path, List<GameSpecDiagnostic> errors) {
        JsonNode value = parent.get(field);
        String fieldPath = path + "/" + field;
        if (value == null || !value.isObject()) {
            error(errors, "GS1002_TYPE_MISMATCH", fieldPath, field + " must be an object");
            return null;
        }
        return (ObjectNode) value;
    }

    private ArrayNode array(ObjectNode parent, String field, String path, int min, int max, List<GameSpecDiagnostic> errors) {
        JsonNode value = parent.get(field);
        String fieldPath = path + "/" + field;
        if (value == null || !value.isArray()) {
            error(errors, "GS1002_TYPE_MISMATCH", fieldPath, field + " must be an array");
            return null;
        }
        if (value.size() < min || value.size() > max) {
            error(errors, "GS1301_VALUE_OUT_OF_RANGE", fieldPath, field + " size must be " + min + ".." + max);
        }
        return (ArrayNode) value;
    }

    private String text(ObjectNode parent, String field, String path, int min, int max, List<GameSpecDiagnostic> errors) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.asText().length() < min || value.asText().length() > max) {
            error(errors, "GS1002_TYPE_MISMATCH", path + "/" + field, field + " must be text of length " + min + ".." + max);
            return null;
        }
        return value.asText();
    }

    private void optionalText(ObjectNode parent, String field, String path, int max, List<GameSpecDiagnostic> errors) {
        if (!parent.has(field)) return;
        JsonNode value = parent.get(field);
        if (!value.isTextual() || value.asText().length() > max) {
            error(errors, "GS1002_TYPE_MISMATCH", path + "/" + field, field + " must be text up to " + max + " characters");
        }
    }

    private Integer integer(ObjectNode parent, String field, String path, int min, int max, List<GameSpecDiagnostic> errors) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber()) {
            error(errors, "GS1002_TYPE_MISMATCH", path + "/" + field, field + " must be an integer");
            return null;
        }
        int number = value.asInt();
        if (number < min || number > max) {
            error(errors, "GS1301_VALUE_OUT_OF_RANGE", path + "/" + field, field + " must be " + min + ".." + max);
            return null;
        }
        return number;
    }

    private String allowed(ObjectNode parent, String field, String path, Set<String> values, List<GameSpecDiagnostic> errors) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || !values.contains(value.asText())) {
            errors.add(new GameSpecDiagnostic("GS1401_UNSUPPORTED_CAPABILITY", ERROR, path + "/" + field,
                    field + " is not registered", values.stream().sorted().toList(), true));
            return null;
        }
        return value.asText();
    }

    private void constant(ObjectNode parent, String field, String path, String expected, List<GameSpecDiagnostic> errors) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || !expected.equals(value.asText())) {
            errors.add(new GameSpecDiagnostic("GS1401_UNSUPPORTED_CAPABILITY", ERROR, path + "/" + field,
                    field + " must be " + expected, List.of(expected), true));
        }
    }

    private void validatePoint(ObjectNode point, String path, Integer width, Integer height, List<GameSpecDiagnostic> errors) {
        if (point == null) return;
        unknown(point, path, Set.of("x", "y"), errors);
        integer(point, "x", path, 1, width == null ? 1919 : width - 1, errors);
        integer(point, "y", path, 1, height == null ? 1079 : height - 1, errors);
    }

    private void forbidden(ObjectNode node, String path, Set<String> fields, List<GameSpecDiagnostic> errors) {
        for (String field : fields) if (node.has(field)) {
            error(errors, "GS1101_UNKNOWN_COMPONENT", path + "/" + field, field + " is not valid for entity type " + node.path("type").asText());
        }
    }

    private void unknown(ObjectNode node, String path, Set<String> allowed, List<GameSpecDiagnostic> errors) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!allowed.contains(field)) {
                errors.add(new GameSpecDiagnostic("GS1001_UNKNOWN_FIELD", ERROR, path + "/" + escape(field),
                        "unknown field", allowed.stream().sorted().toList(), true));
            }
        }
    }

    private List<GameSpecDiagnostic> sorted(List<GameSpecDiagnostic> diagnostics) {
        return diagnostics.stream().sorted(Comparator.comparing(GameSpecDiagnostic::path)
                .thenComparing(GameSpecDiagnostic::code)).toList();
    }

    private void error(List<GameSpecDiagnostic> errors, String code, String path, String message) {
        errors.add(new GameSpecDiagnostic(code, ERROR, path.isEmpty() ? "/" : path, message, List.of(), true));
    }

    private String escape(String value) { return value.replace("~", "~0").replace("/", "~1"); }

    private ObjectNode sortObject(ObjectNode input) {
        ObjectNode output = json.createObjectNode();
        List<String> fields = new ArrayList<>();
        input.fieldNames().forEachRemaining(fields::add);
        fields.stream().sorted().forEach(field -> output.set(field, sort(input.get(field))));
        return output;
    }

    private JsonNode sort(JsonNode input) {
        if (input.isObject()) return sortObject((ObjectNode) input);
        if (input.isArray()) {
            ArrayNode output = json.createArrayNode();
            input.forEach(value -> output.add(sort(value)));
            return output;
        }
        return input.deepCopy();
    }

    private String write(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize GameSpec", exception); }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
