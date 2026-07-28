package com.example.gameworkbench.gameconfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.evaluation.RuleViolation;
import com.example.gameworkbench.evaluation.RuntimeCapabilityRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class ResourceManifestContract {
    public static final String SCHEMA_KEY = "resource-manifest";
    public static final String SCHEMA_VERSION = "1.0";
    private static final Pattern DIGEST = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "runtimeCapabilityVersion",
            "sourceArtifactUuid", "sourceConfigDigest", "resources");
    private static final Set<String> RESOURCE_FIELDS = Set.of("key", "category");

    private final ObjectMapper mapper;
    private final RuntimeCapabilityRegistry capabilities;
    private final GameConfigContract gameConfigContract;

    public ResourceManifestContract(ObjectMapper mapper, RuntimeCapabilityRegistry capabilities,
            GameConfigContract gameConfigContract) {
        this.mapper = mapper;
        this.capabilities = capabilities;
        this.gameConfigContract = gameConfigContract;
    }

    public ResourceManifestContractResult derive(String sourceArtifactUuid, String sourceConfigDigest,
            String canonicalGameConfig) {
        GameConfigContractResult config = gameConfigContract.process(canonicalGameConfig);
        if (!config.valid() || config.migrated()) {
            return new ResourceManifestContractResult(false, null, config.violations());
        }
        Map<String, String> resources = configuredResources(config.canonicalConfig());
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("runtimeCapabilityVersion", capabilities.version());
        root.put("sourceArtifactUuid", sourceArtifactUuid);
        root.put("sourceConfigDigest", sourceConfigDigest);
        ArrayNode entries = root.putArray("resources");
        resources.forEach((key, category) -> entries.addObject().put("key", key).put("category", category));
        return validate(write(root));
    }

    public ResourceManifestContractResult validate(String content) {
        List<RuleViolation> errors = new ArrayList<>();
        JsonNode parsed;
        try {
            parsed = mapper.readTree(content);
        } catch (Exception exception) {
            return failure("INVALID_JSON", "$", "one valid JSON object", "invalid JSON");
        }
        if (!(parsed instanceof ObjectNode root)) return failure("TYPE", "$", "object", parsed.getNodeType().name());
        unknown(root, "$", ROOT_FIELDS, errors);
        constant(root, "schemaVersion", SCHEMA_VERSION, errors);
        constant(root, "runtimeCapabilityVersion", capabilities.version(), errors);
        String sourceUuid = text(root, "sourceArtifactUuid", "$", errors);
        if (sourceUuid != null) try { UUID.fromString(sourceUuid); } catch (IllegalArgumentException exception) {
            add(errors, "FORMAT", "$.sourceArtifactUuid", "UUID", sourceUuid);
        }
        String digest = text(root, "sourceConfigDigest", "$", errors);
        if (digest != null && !DIGEST.matcher(digest).matches()) add(errors, "FORMAT", "$.sourceConfigDigest", "lowercase SHA-256", digest);
        JsonNode resourceNode = root.get("resources");
        if (resourceNode == null || !resourceNode.isArray() || resourceNode.isEmpty()) {
            add(errors, "REQUIRED", "$.resources", "non-empty array", summary(resourceNode));
        } else {
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < resourceNode.size(); index++) {
                JsonNode entryNode = resourceNode.get(index);
                String path = "$.resources[" + index + "]";
                if (!(entryNode instanceof ObjectNode entry)) { add(errors, "TYPE", path, "object", summary(entryNode)); continue; }
                unknown(entry, path, RESOURCE_FIELDS, errors);
                String key = text(entry, "key", path, errors);
                String category = text(entry, "category", path, errors);
                String expected = key == null ? null : GameConfigContract.resourceCategory(key);
                if (key != null && expected == null) add(errors, "RESOURCE_KEY_NOT_ALLOWED", path + ".key", "built-in resource key", key);
                if (expected != null && !expected.equals(category)) add(errors, "RESOURCE_CATEGORY_MISMATCH", path + ".category", expected, category);
                if (key != null && !seen.add(key)) add(errors, "DUPLICATE_RESOURCE", path + ".key", "unique key", key);
            }
        }
        if (!errors.isEmpty()) return new ResourceManifestContractResult(false, null, List.copyOf(errors));
        return new ResourceManifestContractResult(true, canonical(root), List.of());
    }

    private Map<String, String> configuredResources(ObjectNode config) {
        Map<String, String> resources = new TreeMap<>();
        addResource(resources, config.path("player").path("spriteKey").asText());
        config.path("world").path("obstacles").forEach(value -> addResource(resources, value.path("spriteKey").asText()));
        config.path("entities").path("collectibles").forEach(value -> addResource(resources, value.path("spriteKey").asText()));
        config.path("entities").path("enemies").forEach(value -> addResource(resources, value.path("spriteKey").asText()));
        addResource(resources, config.path("entities").path("exit").path("spriteKey").asText());
        config.path("presentation").path("audio").elements().forEachRemaining(value -> addResource(resources, value.asText()));
        return resources;
    }

    private void addResource(Map<String, String> resources, String key) {
        String category = GameConfigContract.resourceCategory(key);
        if (category == null) throw new IllegalStateException("Validated GameConfig contains an unknown resource key");
        resources.put(key, category);
    }

    private String canonical(ObjectNode root) {
        ObjectNode sorted = mapper.createObjectNode();
        new TreeMap<String, JsonNode>(Map.of(
                "schemaVersion", root.get("schemaVersion"),
                "runtimeCapabilityVersion", root.get("runtimeCapabilityVersion"),
                "sourceArtifactUuid", root.get("sourceArtifactUuid"),
                "sourceConfigDigest", root.get("sourceConfigDigest"),
                "resources", root.get("resources"))).forEach(sorted::set);
        return write(sorted);
    }

    private String write(JsonNode value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize resource manifest", exception); }
    }

    private void unknown(ObjectNode node, String path, Set<String> allowed, List<RuleViolation> errors) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!allowed.contains(field)) add(errors, "UNKNOWN_FIELD", path + "." + field, "registered field", field);
        }
    }

    private void constant(ObjectNode root, String field, String expected, List<RuleViolation> errors) {
        String value = text(root, field, "$", errors);
        if (value != null && !expected.equals(value)) add(errors, "CONST", "$." + field, expected, value);
    }

    private String text(ObjectNode root, String field, String path, List<RuleViolation> errors) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            add(errors, "REQUIRED", path + "." + field, "non-blank string", summary(value));
            return null;
        }
        return value.asText();
    }

    private ResourceManifestContractResult failure(String code, String path, String expected, String actual) {
        return new ResourceManifestContractResult(false, null, List.of(new RuleViolation(code, path, "BLOCKING", expected, actual)));
    }

    private void add(List<RuleViolation> errors, String code, String path, String expected, String actual) {
        errors.add(new RuleViolation(code, path, "BLOCKING", expected, actual));
    }

    private String summary(JsonNode value) {
        return value == null ? "missing" : value.isValueNode() ? value.toString() : value.getNodeType().name();
    }
}
