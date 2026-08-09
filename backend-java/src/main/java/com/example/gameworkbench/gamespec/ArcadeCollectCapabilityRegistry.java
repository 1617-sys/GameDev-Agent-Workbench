package com.example.gameworkbench.gamespec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public final class ArcadeCollectCapabilityRegistry {
    public static final String VERSION = "arcade-collect/1";
    public static final String SPEC_VERSION = "0.1";
    public static final String ARCHETYPE = "arcade_collect";
    public static final String COCOS_VERSION = "3.8.8";
    public static final String RUNTIME_SHELL_VERSION = "cocos-arcade-collect/1";
    public static final String BUILD_PROFILE_VERSION = "local-web-mobile/1";

    private static final Set<String> ENTITY_TYPES = Set.of("collectible", "enemy", "obstacle", "exit");
    private static final Set<String> MOVEMENTS = Set.of("four_way");
    private static final Set<String> THEMES = Set.of("forest-01");
    private static final Set<String> ASSET_PACKS = Set.of("forest-adventure-01");
    private static final Set<String> ANIMATIONS = Set.of("topdown-character-01");
    private static final Set<String> CAMERAS = Set.of("follow-soft-01");
    private static final Set<String> FEEDBACK = Set.of("arcade-juice-01");
    private static final Set<String> UI_SKINS = Set.of("forest-hud-01");
    private static final Set<String> AUDIO = Set.of("forest-light-01");

    private final ObjectMapper json;

    public ArcadeCollectCapabilityRegistry(ObjectMapper json) {
        this.json = json;
    }

    public Set<String> entityTypes() { return ENTITY_TYPES; }
    public Set<String> movements() { return MOVEMENTS; }

    public Set<String> allowedProfileValues(String field) {
        return switch (field) {
            case "visualThemeId" -> THEMES;
            case "assetPackId" -> ASSET_PACKS;
            case "animationProfileId" -> ANIMATIONS;
            case "cameraProfileId" -> CAMERAS;
            case "feedbackProfileId" -> FEEDBACK;
            case "uiSkinId" -> UI_SKINS;
            case "audioProfileId" -> AUDIO;
            default -> Set.of();
        };
    }

    public ObjectNode snapshot() {
        ObjectNode root = json.createObjectNode();
        root.put("registryVersion", VERSION);
        root.put("specVersion", SPEC_VERSION);
        root.put("archetype", ARCHETYPE);
        root.put("runtimeShellVersion", RUNTIME_SHELL_VERSION);
        root.put("cocosCreatorVersion", COCOS_VERSION);
        root.put("buildTarget", "web-mobile");
        array(root, "entityTypes", ENTITY_TYPES);
        array(root, "movements", MOVEMENTS);
        ObjectNode profiles = root.putObject("profiles");
        for (String key : List.of("visualThemeId", "assetPackId", "animationProfileId", "cameraProfileId",
                "feedbackProfileId", "uiSkinId", "audioProfileId")) {
            array(profiles, key, allowedProfileValues(key));
        }
        root.put("digest", digest(canonicalWithoutDigest(root)));
        return root;
    }

    private void array(ObjectNode parent, String name, Set<String> values) {
        ArrayNode array = parent.putArray(name);
        values.stream().sorted().forEach(array::add);
    }

    private String canonicalWithoutDigest(ObjectNode value) {
        try {
            Map<String, Object> map = json.convertValue(value, TreeMap.class);
            return json.writeValueAsString(map);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to canonicalize capability registry", exception);
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
