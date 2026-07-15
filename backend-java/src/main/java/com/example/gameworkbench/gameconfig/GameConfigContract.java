package com.example.gameworkbench.gameconfig;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.evaluation.RuleViolation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class GameConfigContract {
    public static final String SCHEMA_KEY = "game-config";
    public static final String SCHEMA_VERSION = "2.0";
    public static final String RUN_SCHEMA_VERSION = "game-config/2.0";
    public static final String LEGACY_RUN_SCHEMA_VERSION = "game-config/1.0";
    public static final String GAME_TYPE = "arcade_collect";

    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Set<String> ROOT_FIELDS = Set.of("metadata", "viewport", "world", "player", "entities",
            "behaviors", "objectives", "balance", "presentation", "telemetry");
    private static final List<String> TELEMETRY_EVENTS = List.of("SESSION_STARTED", "ITEM_COLLECTED", "PLAYER_HIT",
            "GAME_WON", "GAME_LOST", "SESSION_RESTARTED", "SESSION_ENDED");
    private static final Set<String> PLAYER_KEYS = Set.of("player.blue", "player.green");
    private static final Set<String> COLLECTIBLE_KEYS = Set.of("collectible.gem", "collectible.artifact", "collectible.core");
    private static final Set<String> ENEMY_KEYS = Set.of("enemy.guard", "enemy.drone");
    private static final Set<String> EXIT_KEYS = Set.of("exit.portal", "exit.door");
    private static final Set<String> OBSTACLE_KEYS = Set.of("obstacle.stone", "obstacle.metal", "obstacle.wood");
    private static final Set<String> SOUND_KEYS = Set.of("sfx.collect", "sfx.hit", "sfx.win", "sfx.lose", "sfx.silent");
    private static final Map<String, String> DEFAULT_PALETTE = Map.of(
            "floor", "#14213D", "wall", "#24324A", "player", "#5EEAD4",
            "item", "#FACC15", "enemy", "#FB7185", "exit", "#22C55E");

    private final ObjectMapper mapper;

    public GameConfigContract(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public GameConfigContractResult process(String raw) {
        List<RuleViolation> errors = new ArrayList<>();
        ObjectNode source = extract(raw, errors);
        if (source == null) return new GameConfigContractResult(null, List.copyOf(errors), false);

        boolean v2 = "2.0".equals(source.path("metadata").path("schemaVersion").asText(null));
        boolean legacy = "1.0".equals(source.path("version").asText(null));
        if (!v2 && !legacy) {
            add(errors, "UNSUPPORTED_SCHEMA_VERSION", "$", "GameConfig 2.0 or migratable 1.0", versionSummary(source));
            return new GameConfigContractResult(null, List.copyOf(errors), false);
        }

        ObjectNode candidate = legacy ? migrateLegacy(source, errors) : source.deepCopy();
        if (candidate != null) validateV2(candidate, errors);
        if (!errors.isEmpty()) return new GameConfigContractResult(null, List.copyOf(errors), legacy);
        normalizeV2(candidate);
        return new GameConfigContractResult(sortObject(candidate), List.of(), legacy);
    }

    public String canonicalJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(sortObject(node));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize canonical GameConfig", exception);
        }
    }

    private ObjectNode extract(String raw, List<RuleViolation> errors) {
        JsonNode node;
        try {
            node = mapper.readTree(raw);
        } catch (Exception exception) {
            add(errors, "INVALID_JSON", "$", "one valid JSON object", "invalid JSON");
            return null;
        }
        for (int depth = 0; depth < 4; depth++) {
            JsonNode wrapped = wrapped(node);
            if (wrapped == null) break;
            node = wrapped;
        }
        if (wrapped(node) != null) {
            add(errors, "WRAPPER_DEPTH", "$", "at most four registered wrappers", "wrapper depth exceeded");
            return null;
        }
        if (node == null || !node.isObject()) {
            add(errors, "JSON_OBJECT_REQUIRED", "$", "one JSON object", summary(node));
            return null;
        }
        return ((ObjectNode) node).deepCopy();
    }

    private JsonNode wrapped(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        if (node.has("game_config")) return node.get("game_config");
        if (node.has("gameConfig")) return node.get("gameConfig");
        if (node.has("data")) return node.get("data");
        if (node.path("raw_result").has("game_config")) return node.path("raw_result").get("game_config");
        if (node.path("rawResult").has("gameConfig")) return node.path("rawResult").get("gameConfig");
        return null;
    }

    private void validateV2(ObjectNode root, List<RuleViolation> errors) {
        unknown(root, "$", ROOT_FIELDS, errors);
        ObjectNode metadata = object(root, "metadata", "$", errors);
        ObjectNode viewport = object(root, "viewport", "$", errors);
        ObjectNode world = object(root, "world", "$", errors);
        ObjectNode player = object(root, "player", "$", errors);
        ObjectNode entities = object(root, "entities", "$", errors);
        ObjectNode behaviors = object(root, "behaviors", "$", errors);
        ObjectNode objectives = object(root, "objectives", "$", errors);
        ObjectNode balance = object(root, "balance", "$", errors);
        ObjectNode presentation = object(root, "presentation", "$", errors);
        ObjectNode telemetry = object(root, "telemetry", "$", errors);

        if (metadata != null) {
            unknown(metadata, "$.metadata", Set.of("schemaVersion", "gameType", "title", "seed"), errors);
            constantText(metadata, "schemaVersion", "$.metadata", "2.0", errors);
            constantText(metadata, "gameType", "$.metadata", GAME_TYPE, errors);
            safeText(metadata, "title", "$.metadata", 80, errors);
            integer(metadata, "seed", "$.metadata", 0, 2_147_483_647, errors);
        }

        Integer viewportWidth = null, viewportHeight = null;
        if (viewport != null) {
            unknown(viewport, "$.viewport", Set.of("width", "height", "scaleMode"), errors);
            viewportWidth = integer(viewport, "width", "$.viewport", 640, 1280, errors);
            viewportHeight = integer(viewport, "height", "$.viewport", 360, 720, errors);
            constantText(viewport, "scaleMode", "$.viewport", "fit", errors);
            if (viewportWidth != null && viewportHeight != null
                    && Math.abs((double) viewportWidth / viewportHeight - 16.0 / 9.0) / (16.0 / 9.0) > 0.01) {
                add(errors, "VIEWPORT_RATIO", "$.viewport", "16:9 within 1%", viewportWidth + "x" + viewportHeight);
            }
        }

        Integer worldWidth = null, worldHeight = null;
        ObjectNode spawn = null;
        ArrayNode obstacles = null;
        if (world != null) {
            unknown(world, "$.world", Set.of("width", "height", "spawn", "obstacles"), errors);
            worldWidth = integer(world, "width", "$.world", 640, 1280, errors);
            worldHeight = integer(world, "height", "$.world", 360, 720, errors);
            spawn = object(world, "spawn", "$.world", errors);
            obstacles = array(world, "obstacles", "$.world", 0, 16, errors);
            point(spawn, "$.world.spawn", errors);
            validateObstacles(obstacles, errors);
        }
        if (viewportWidth != null && worldWidth != null && !viewportWidth.equals(worldWidth)
                || viewportHeight != null && worldHeight != null && !viewportHeight.equals(worldHeight)) {
            add(errors, "WORLD_VIEWPORT_MISMATCH", "$.world", "world dimensions equal viewport", summary(world));
        }

        Integer playerSize = null;
        if (player != null) {
            unknown(player, "$.player", Set.of("speed", "size", "maxHealth", "hitInvulnerabilityMs", "spriteKey"), errors);
            integer(player, "speed", "$.player", 80, 400, errors);
            playerSize = integer(player, "size", "$.player", 24, 64, errors);
            integer(player, "maxHealth", "$.player", 1, 5, errors);
            integer(player, "hitInvulnerabilityMs", "$.player", 0, 3000, errors);
            resource(player, "spriteKey", "$.player", PLAYER_KEYS, errors);
        }

        ArrayNode collectibles = null, enemies = null;
        ObjectNode exit = null;
        if (entities != null) {
            unknown(entities, "$.entities", Set.of("collectibles", "enemies", "exit"), errors);
            collectibles = array(entities, "collectibles", "$.entities", 1, 20, errors);
            enemies = array(entities, "enemies", "$.entities", 0, 12, errors);
            exit = object(entities, "exit", "$.entities", errors);
            validateCollectibles(collectibles, errors);
            validateEnemies(enemies, errors);
            validateExit(exit, errors);
        }

        ArrayNode patrols = null;
        if (behaviors != null) {
            unknown(behaviors, "$.behaviors", Set.of("enemyPatrols", "contact"), errors);
            patrols = array(behaviors, "enemyPatrols", "$.behaviors", 0, 12, errors);
            ObjectNode contact = object(behaviors, "contact", "$.behaviors", errors);
            validatePatrols(patrols, errors);
            if (contact != null) {
                unknown(contact, "$.behaviors.contact", Set.of("damage"), errors);
                integer(contact, "damage", "$.behaviors.contact", 1, 5, errors);
            }
        }

        if (objectives != null) {
            unknown(objectives, "$.objectives", Set.of("targetCollectibles", "winCondition", "loseConditions"), errors);
            Integer target = integer(objectives, "targetCollectibles", "$.objectives", 1, 20, errors);
            constantText(objectives, "winCondition", "$.objectives", "collect_target_then_exit", errors);
            ArrayNode conditions = array(objectives, "loseConditions", "$.objectives", 1, 2, errors);
            enumArray(conditions, "$.objectives.loseConditions", Set.of("health_depleted", "time_expired"), errors);
            if (target != null && collectibles != null && target > collectibles.size()) {
                add(errors, "TARGET_EXCEEDS_COLLECTIBLES", "$.objectives.targetCollectibles",
                        "1..collectibles.length", String.valueOf(target));
            }
        }

        if (balance != null) {
            unknown(balance, "$.balance", Set.of("timeLimitSeconds", "winBonus", "difficulty"), errors);
            integer(balance, "timeLimitSeconds", "$.balance", 30, 600, errors);
            integer(balance, "winBonus", "$.balance", 0, 10_000, errors);
            enumText(balance, "difficulty", "$.balance", Set.of("easy", "normal", "hard"), errors);
        }
        validatePresentation(presentation, errors);
        validateTelemetry(telemetry, errors);

        if (worldWidth != null && worldHeight != null) {
            validateGeometry(worldWidth, worldHeight, spawn, playerSize, obstacles, collectibles, enemies, exit, patrols, errors);
        }
        validateReferences(enemies, patrols, errors);
    }

    private void validateObstacles(ArrayNode values, List<RuleViolation> errors) {
        if (values == null) return;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String path = "$.world.obstacles[" + i + "]";
            ObjectNode value = arrayObject(values, i, path, errors);
            if (value == null) continue;
            unknown(value, path, Set.of("id", "x", "y", "width", "height", "spriteKey"), errors);
            uniqueId(value, "id", path, ids, errors); point(value, path, errors);
            integer(value, "width", path, 24, 320, errors); integer(value, "height", path, 24, 320, errors);
            resource(value, "spriteKey", path, OBSTACLE_KEYS, errors);
        }
    }

    private void validateCollectibles(ArrayNode values, List<RuleViolation> errors) {
        if (values == null) return;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String path = "$.entities.collectibles[" + i + "]";
            ObjectNode value = arrayObject(values, i, path, errors);
            if (value == null) continue;
            unknown(value, path, Set.of("id", "x", "y", "size", "score", "label", "spriteKey"), errors);
            uniqueId(value, "id", path, ids, errors); point(value, path, errors);
            integer(value, "size", path, 12, 48, errors); integer(value, "score", path, 1, 1000, errors);
            safeText(value, "label", path, 80, errors); resource(value, "spriteKey", path, COLLECTIBLE_KEYS, errors);
        }
    }

    private void validateEnemies(ArrayNode values, List<RuleViolation> errors) {
        if (values == null) return;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String path = "$.entities.enemies[" + i + "]";
            ObjectNode value = arrayObject(values, i, path, errors);
            if (value == null) continue;
            unknown(value, path, Set.of("id", "x", "y", "size", "speed", "spriteKey"), errors);
            uniqueId(value, "id", path, ids, errors); point(value, path, errors);
            integer(value, "size", path, 24, 64, errors); integer(value, "speed", path, 20, 240, errors);
            resource(value, "spriteKey", path, ENEMY_KEYS, errors);
        }
    }

    private void validateExit(ObjectNode value, List<RuleViolation> errors) {
        if (value == null) return;
        String path = "$.entities.exit";
        unknown(value, path, Set.of("x", "y", "width", "height", "label", "spriteKey"), errors);
        point(value, path, errors); integer(value, "width", path, 32, 160, errors);
        integer(value, "height", path, 32, 160, errors); safeText(value, "label", path, 80, errors);
        resource(value, "spriteKey", path, EXIT_KEYS, errors);
    }

    private void validatePatrols(ArrayNode values, List<RuleViolation> errors) {
        if (values == null) return;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String path = "$.behaviors.enemyPatrols[" + i + "]";
            ObjectNode value = arrayObject(values, i, path, errors);
            if (value == null) continue;
            unknown(value, path, Set.of("enemyId", "axis", "distance"), errors);
            uniqueId(value, "enemyId", path, ids, errors);
            enumText(value, "axis", path, Set.of("x", "y"), errors);
            integer(value, "distance", path, 32, 480, errors);
        }
    }

    private void validatePresentation(ObjectNode value, List<RuleViolation> errors) {
        if (value == null) return;
        unknown(value, "$.presentation", Set.of("palette", "audio", "ui"), errors);
        ObjectNode palette = object(value, "palette", "$.presentation", errors);
        ObjectNode audio = object(value, "audio", "$.presentation", errors);
        ObjectNode ui = object(value, "ui", "$.presentation", errors);
        if (palette != null) {
            unknown(palette, "$.presentation.palette", Set.of("floor", "wall", "player", "item", "enemy", "exit"), errors);
            for (String field : List.of("floor", "wall", "player", "item", "enemy", "exit")) color(palette, field, "$.presentation.palette", errors);
        }
        if (audio != null) {
            unknown(audio, "$.presentation.audio", Set.of("collect", "hit", "win", "lose"), errors);
            for (String field : List.of("collect", "hit", "win", "lose")) resource(audio, field, "$.presentation.audio", SOUND_KEYS, errors);
        }
        if (ui != null) {
            unknown(ui, "$.presentation.ui", Set.of("objective", "controls"), errors);
            safeText(ui, "objective", "$.presentation.ui", 160, errors);
            safeText(ui, "controls", "$.presentation.ui", 160, errors);
        }
    }

    private void validateTelemetry(ObjectNode value, List<RuleViolation> errors) {
        if (value == null) return;
        unknown(value, "$.telemetry", Set.of("events"), errors);
        ArrayNode events = array(value, "events", "$.telemetry", 7, 7, errors);
        if (events == null) return;
        Set<String> actual = new LinkedHashSet<>();
        for (JsonNode event : events) if (event.isTextual()) actual.add(event.asText());
        if (actual.size() != 7 || !actual.equals(new LinkedHashSet<>(TELEMETRY_EVENTS))) {
            add(errors, "TELEMETRY_EVENTS", "$.telemetry.events", TELEMETRY_EVENTS.toString(), summary(events));
        }
    }

    private void validateReferences(ArrayNode enemies, ArrayNode patrols, List<RuleViolation> errors) {
        if (enemies == null || patrols == null) return;
        Set<String> enemyIds = ids(enemies, "id");
        Set<String> patrolIds = ids(patrols, "enemyId");
        if (enemyIds.size() != enemies.size() || patrolIds.size() != patrols.size() || !enemyIds.equals(patrolIds)) {
            add(errors, "PATROL_REFERENCE", "$.behaviors.enemyPatrols", "exactly one patrol per enemy", patrolIds.toString());
        }
    }

    private void validateGeometry(int width, int height, ObjectNode spawn, Integer playerSize, ArrayNode obstacles,
            ArrayNode collectibles, ArrayNode enemies, ObjectNode exit, ArrayNode patrols, List<RuleViolation> errors) {
        if (spawn != null && playerSize != null) circleBounds(spawn, playerSize / 2.0, width, height, "$.world.spawn", errors);
        rectArrayBounds(obstacles, width, height, "$.world.obstacles", errors);
        circleArrayBounds(collectibles, width, height, "$.entities.collectibles", errors);
        circleArrayBounds(enemies, width, height, "$.entities.enemies", errors);
        if (exit != null) rectBounds(exit, width, height, "$.entities.exit", errors);
        if (enemies != null && patrols != null) {
            Map<String, ObjectNode> enemyById = index(enemies, "id");
            for (int i = 0; i < patrols.size(); i++) {
                if (!patrols.get(i).isObject()) continue;
                ObjectNode patrol = (ObjectNode) patrols.get(i);
                ObjectNode enemy = enemyById.get(patrol.path("enemyId").asText());
                if (enemy == null || !number(enemy.get("x")) || !number(enemy.get("y"))
                        || !integerNode(enemy.get("size")) || !integerNode(patrol.get("distance"))) continue;
                double center = "y".equals(patrol.path("axis").asText()) ? enemy.path("y").doubleValue() : enemy.path("x").doubleValue();
                double radius = enemy.path("size").intValue() / 2.0;
                int distance = patrol.path("distance").intValue();
                int limit = "y".equals(patrol.path("axis").asText()) ? height : width;
                if (center - distance - radius < 0 || center + distance + radius > limit) {
                    add(errors, "WORLD_BOUNDS", "$.behaviors.enemyPatrols[" + i + "].distance",
                            "patrol body inside world", String.valueOf(distance));
                }
            }
        }
        if (obstacles != null) {
            for (int obstacleIndex = 0; obstacleIndex < obstacles.size(); obstacleIndex++) {
                if (!obstacles.get(obstacleIndex).isObject()) continue;
                ObjectNode obstacle = (ObjectNode) obstacles.get(obstacleIndex);
                if (spawn != null && playerSize != null && circleRectOverlap(spawn, playerSize / 2.0, obstacle))
                    add(errors, "WORLD_OVERLAP", "$.world.spawn", "spawn outside obstacles", "obstacle " + obstacleIndex);
                overlapCircles(obstacle, collectibles, "$.entities.collectibles", obstacleIndex, errors);
                overlapCircles(obstacle, enemies, "$.entities.enemies", obstacleIndex, errors);
                if (exit != null && rectOverlap(obstacle, exit))
                    add(errors, "WORLD_OVERLAP", "$.entities.exit", "exit outside obstacles", "obstacle " + obstacleIndex);
            }
        }
    }

    private ObjectNode migrateLegacy(ObjectNode legacy, List<RuleViolation> errors) {
        unknown(legacy, "$", Set.of("version", "title", "gameType", "game_type", "world", "theme", "player",
                "obstacles", "items", "collectibles", "enemies", "exit", "rules", "ui"), errors);
        constantText(legacy, "version", "$", "1.0", errors);
        String gameType = aliasText(legacy, "gameType", "game_type", "$", errors);
        if (gameType == null || !"top_down_collect".equals(gameType))
            add(errors, "UNSUPPORTED_GAME_TYPE", "$.gameType", "top_down_collect", String.valueOf(gameType));
        String title = safeText(legacy, "title", "$", 80, errors);
        ObjectNode world = object(legacy, "world", "$", errors);
        ObjectNode player = object(legacy, "player", "$", errors);
        ArrayNode obstacles = optionalArray(legacy, "obstacles", "$", 0, 16, errors);
        ArrayNode items = aliasArray(legacy, "items", "collectibles", "$", 1, 20, errors);
        ArrayNode enemies = array(legacy, "enemies", "$", 0, 12, errors);
        ObjectNode exit = object(legacy, "exit", "$", errors);
        ObjectNode rules = object(legacy, "rules", "$", errors);
        ObjectNode ui = object(legacy, "ui", "$", errors);
        ObjectNode theme = optionalObject(legacy, "theme", "$", errors);

        validateLegacyWorld(world, errors); validateLegacyPlayer(player, errors);
        validateLegacyObstacles(obstacles, errors); validateLegacyItems(items, errors);
        validateLegacyEnemies(enemies, errors); validateLegacyExit(exit, errors);
        validateLegacyRules(rules, errors); validateLegacyUi(ui, errors); validateLegacyTheme(theme, errors);
        if (!errors.isEmpty()) return null;

        ObjectNode out = mapper.createObjectNode();
        ObjectNode metadata = out.putObject("metadata");
        metadata.put("schemaVersion", "2.0").put("gameType", GAME_TYPE).put("title", title).put("seed", legacySeed(legacy));
        int width = world.path("width").intValue(), height = world.path("height").intValue();
        out.putObject("viewport").put("width", width).put("height", height).put("scaleMode", "fit");
        ObjectNode outWorld = out.putObject("world").put("width", width).put("height", height);
        ObjectNode outSpawn = outWorld.putObject("spawn");
        outSpawn.set("x", player.get("x").deepCopy());
        outSpawn.set("y", player.get("y").deepCopy());
        ArrayNode outObstacles = outWorld.putArray("obstacles");
        if (obstacles != null) for (JsonNode node : obstacles) {
            ObjectNode value = (ObjectNode) node; ObjectNode target = outObstacles.addObject();
            copy(target, value, "id", "x", "y", "width", "height"); target.put("spriteKey", "obstacle.stone");
        }
        ObjectNode outPlayer = out.putObject("player");
        outPlayer.put("speed", player.path("speed").intValue()).put("size", player.has("size") ? player.path("size").intValue() : 28)
                .put("maxHealth", 1).put("hitInvulnerabilityMs", 0).put("spriteKey", "player.blue");
        ObjectNode outEntities = out.putObject("entities");
        ArrayNode outItems = outEntities.putArray("collectibles");
        for (int i = 0; i < items.size(); i++) {
            ObjectNode value = (ObjectNode) items.get(i); ObjectNode target = outItems.addObject();
            copy(target, value, "id", "x", "y"); target.put("size", value.has("size") ? value.path("size").intValue() : 18)
                    .put("score", 100).put("label", value.has("label") ? value.path("label").asText() : "目标 " + (i + 1))
                    .put("spriteKey", "collectible.gem");
        }
        ArrayNode outEnemies = outEntities.putArray("enemies");
        ArrayNode outPatrols = out.putObject("behaviors").putArray("enemyPatrols");
        for (JsonNode node : enemies) {
            ObjectNode value = (ObjectNode) node; ObjectNode target = outEnemies.addObject();
            copy(target, value, "id", "x", "y", "speed"); target.put("size", value.has("size") ? value.path("size").intValue() : 28)
                    .put("spriteKey", "enemy.guard");
            ObjectNode patrol = outPatrols.addObject().put("enemyId", value.path("id").asText());
            patrol.put("axis", aliasText(value, "axis", "patrolAxis", "$.enemies", new ArrayList<>()));
            JsonNode distance = alias(value, "range", "patrolDistance", "$.enemies", new ArrayList<>());
            patrol.set("distance", distance.deepCopy());
        }
        ((ObjectNode) out.get("behaviors")).putObject("contact").put("damage", 1);
        ObjectNode outExit = outEntities.putObject("exit"); copy(outExit, exit, "x", "y");
        outExit.put("width", exit.has("width") ? exit.path("width").intValue() : 54)
                .put("height", exit.has("height") ? exit.path("height").intValue() : 72)
                .put("label", exit.has("label") ? exit.path("label").asText() : "EXIT").put("spriteKey", "exit.door");
        out.putObject("objectives").put("targetCollectibles", rules.path("targetItems").intValue())
                .put("winCondition", "collect_target_then_exit").putArray("loseConditions").add("health_depleted");
        out.putObject("balance").put("timeLimitSeconds", 90).put("winBonus", 500).put("difficulty", "normal");
        ObjectNode presentation = out.putObject("presentation"); ObjectNode palette = presentation.putObject("palette");
        ObjectNode legacyPalette = theme == null ? null : optionalObject(theme, "palette", "$.theme", new ArrayList<>());
        for (String field : List.of("floor", "wall", "player", "item", "enemy", "exit"))
            palette.put(field, legacyPalette != null && legacyPalette.has(field) ? legacyPalette.path(field).asText() :
                    "floor".equals(field) && world.has("backgroundColor") && COLOR.matcher(world.path("backgroundColor").asText()).matches()
                            ? world.path("backgroundColor").asText() : DEFAULT_PALETTE.get(field));
        presentation.putObject("audio").put("collect", "sfx.collect").put("hit", "sfx.hit").put("win", "sfx.win").put("lose", "sfx.lose");
        ObjectNode outUi = presentation.putObject("ui").put("objective", ui.path("objective").asText());
        outUi.put("controls", aliasText(ui, "controls", "controlHint", "$.ui", new ArrayList<>()));
        ArrayNode telemetry = out.putObject("telemetry").putArray("events"); TELEMETRY_EVENTS.forEach(telemetry::add);
        return out;
    }

    private void validateLegacyWorld(ObjectNode value, List<RuleViolation> errors) {
        if (value == null) return; unknown(value, "$.world", Set.of("width", "height", "backgroundColor"), errors);
        integer(value, "width", "$.world", 640, 1280, errors); integer(value, "height", "$.world", 360, 720, errors);
        if (value.has("backgroundColor") && (!value.get("backgroundColor").isTextual()
                || !COLOR.matcher(value.get("backgroundColor").asText()).matches()))
            add(errors, "COLOR", "$.world.backgroundColor", "#RRGGBB", summary(value.get("backgroundColor")));
    }
    private void validateLegacyPlayer(ObjectNode value, List<RuleViolation> errors) {
        if (value == null) return; unknown(value, "$.player", Set.of("x", "y", "speed", "size"), errors); point(value, "$.player", errors);
        integer(value, "speed", "$.player", 80, 400, errors); optionalInteger(value, "size", "$.player", 24, 64, errors);
    }
    private void validateLegacyObstacles(ArrayNode values, List<RuleViolation> errors) {
        if (values == null) return; Set<String> ids = new HashSet<>();
        for (int i=0;i<values.size();i++){String p="$.obstacles["+i+"]";ObjectNode v=arrayObject(values,i,p,errors);if(v==null)continue;
            unknown(v,p,Set.of("id","x","y","width","height"),errors);uniqueId(v,"id",p,ids,errors);point(v,p,errors);
            integer(v,"width",p,24,320,errors);integer(v,"height",p,24,320,errors);}
    }
    private void validateLegacyItems(ArrayNode values, List<RuleViolation> errors) {
        if(values==null)return;Set<String>ids=new HashSet<>();for(int i=0;i<values.size();i++){String p="$.items["+i+"]";ObjectNode v=arrayObject(values,i,p,errors);if(v==null)continue;
            unknown(v,p,Set.of("id","x","y","size","label"),errors);uniqueId(v,"id",p,ids,errors);point(v,p,errors);optionalInteger(v,"size",p,12,48,errors);
            if(v.has("label"))safeText(v,"label",p,80,errors);}
    }
    private void validateLegacyEnemies(ArrayNode values, List<RuleViolation> errors) {
        if(values==null)return;Set<String>ids=new HashSet<>();for(int i=0;i<values.size();i++){String p="$.enemies["+i+"]";ObjectNode v=arrayObject(values,i,p,errors);if(v==null)continue;
            unknown(v,p,Set.of("id","x","y","size","speed","axis","patrolAxis","range","patrolDistance"),errors);uniqueId(v,"id",p,ids,errors);point(v,p,errors);
            optionalInteger(v,"size",p,24,64,errors);integer(v,"speed",p,20,240,errors);String axis=aliasText(v,"axis","patrolAxis",p,errors);
            if(axis==null||!Set.of("x","y").contains(axis))add(errors,"ENUM",p+".axis","x or y",String.valueOf(axis));JsonNode d=alias(v,"range","patrolDistance",p,errors);
            if(!integerNode(d)||d.intValue()<32||d.intValue()>480)add(errors,"RANGE",p+".range","integer 32..480",summary(d));}
    }
    private void validateLegacyExit(ObjectNode v,List<RuleViolation>e){if(v==null)return;unknown(v,"$.exit",Set.of("x","y","width","height","label"),e);point(v,"$.exit",e);optionalInteger(v,"width","$.exit",32,160,e);optionalInteger(v,"height","$.exit",32,160,e);if(v.has("label"))safeText(v,"label","$.exit",80,e);}
    private void validateLegacyRules(ObjectNode v,List<RuleViolation>e){if(v==null)return;unknown(v,"$.rules",Set.of("targetItems","winCondition","loseCondition"),e);integer(v,"targetItems","$.rules",1,20,e);constantText(v,"winCondition","$.rules","collect_all_then_exit",e);constantText(v,"loseCondition","$.rules","touch_enemy",e);}
    private void validateLegacyUi(ObjectNode v,List<RuleViolation>e){if(v==null)return;unknown(v,"$.ui",Set.of("objective","controls","controlHint"),e);safeText(v,"objective","$.ui",160,e);String controls=aliasText(v,"controls","controlHint","$.ui",e);if(controls==null||!safeString(controls,160))add(e,"TEXT","$.ui.controls","safe text 1..160",String.valueOf(controls));}
    private void validateLegacyTheme(ObjectNode v,List<RuleViolation>e){if(v==null)return;unknown(v,"$.theme",Set.of("palette"),e);ObjectNode p=optionalObject(v,"palette","$.theme",e);if(p!=null){unknown(p,"$.theme.palette",Set.of("floor","wall","player","item","enemy","exit"),e);for(String f:List.of("floor","wall","player","item","enemy","exit"))if(p.has(f))color(p,f,"$.theme.palette",e);}}

    private void normalizeV2(ObjectNode root) {
        ObjectNode palette = (ObjectNode) root.path("presentation").path("palette");
        for (String field : List.of("floor", "wall", "player", "item", "enemy", "exit"))
            palette.put(field, palette.path(field).asText().toUpperCase());
        ArrayNode conditions = (ArrayNode) root.path("objectives").path("loseConditions");
        List<String> sorted = new ArrayList<>(); conditions.forEach(n -> sorted.add(n.asText()));
        sorted.sort(Comparator.comparingInt(value -> "health_depleted".equals(value) ? 0 : 1));
        conditions.removeAll(); sorted.forEach(conditions::add);
        ArrayNode events = (ArrayNode) root.path("telemetry").path("events"); events.removeAll(); TELEMETRY_EVENTS.forEach(events::add);
    }

    private ObjectNode sortObject(ObjectNode value) {
        ObjectNode sorted = mapper.createObjectNode(); TreeMap<String, JsonNode> fields = new TreeMap<>();
        value.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
        fields.forEach((key, node) -> sorted.set(key, sortNode(node))); return sorted;
    }
    private JsonNode sortNode(JsonNode node) {if(node.isObject())return sortObject((ObjectNode)node);if(node.isArray()){ArrayNode a=mapper.createArrayNode();node.forEach(n->a.add(sortNode(n)));return a;}return node.deepCopy();}

    private ObjectNode object(ObjectNode parent,String field,String path,List<RuleViolation>e){JsonNode n=parent.get(field);if(n==null||!n.isObject()){add(e,"REQUIRED",path+"."+field,"object",summary(n));return null;}return(ObjectNode)n;}
    private ObjectNode optionalObject(ObjectNode parent,String field,String path,List<RuleViolation>e){if(!parent.has(field))return null;JsonNode n=parent.get(field);if(!n.isObject()){add(e,"TYPE",path+"."+field,"object",summary(n));return null;}return(ObjectNode)n;}
    private ArrayNode array(ObjectNode p,String f,String path,int min,int max,List<RuleViolation>e){JsonNode n=p.get(f);if(n==null||!n.isArray()){add(e,"REQUIRED",path+"."+f,"array",summary(n));return null;}if(n.size()<min||n.size()>max)add(e,"ARRAY_SIZE",path+"."+f,min+".."+max, String.valueOf(n.size()));return(ArrayNode)n;}
    private ArrayNode optionalArray(ObjectNode p,String f,String path,int min,int max,List<RuleViolation>e){if(!p.has(f))return mapper.createArrayNode();return array(p,f,path,min,max,e);}
    private ArrayNode aliasArray(ObjectNode p,String a,String b,String path,int min,int max,List<RuleViolation>e){JsonNode n=alias(p,a,b,path,e);if(n==null||!n.isArray()){add(e,"REQUIRED",path+"."+a,"array",summary(n));return null;}if(n.size()<min||n.size()>max)add(e,"ARRAY_SIZE",path+"."+a,min+".."+max,String.valueOf(n.size()));return(ArrayNode)n;}
    private ObjectNode arrayObject(ArrayNode a,int i,String path,List<RuleViolation>e){JsonNode n=a.get(i);if(!n.isObject()){add(e,"TYPE",path,"object",summary(n));return null;}return(ObjectNode)n;}
    private void unknown(ObjectNode n,String path,Set<String>allowed,List<RuleViolation>e){Iterator<String>it=n.fieldNames();while(it.hasNext()){String f=it.next();if(!allowed.contains(f))add(e,"UNKNOWN_FIELD",path+"."+f,"registered field",f);}}
    private Integer integer(ObjectNode p,String f,String path,int min,int max,List<RuleViolation>e){JsonNode n=p.get(f);if(!integerNode(n)||n.longValue()<min||n.longValue()>max){add(e,n==null?"REQUIRED":"RANGE",path+"."+f,"integer "+min+".."+max,summary(n));return null;}return n.intValue();}
    private void optionalInteger(ObjectNode p,String f,String path,int min,int max,List<RuleViolation>e){if(p.has(f))integer(p,f,path,min,max,e);}
    private boolean integerNode(JsonNode n){return n!=null&&n.isIntegralNumber()&&n.canConvertToInt();}
    private boolean number(JsonNode n){return n!=null&&n.isNumber()&&Double.isFinite(n.doubleValue());}
    private void point(ObjectNode p,String path,List<RuleViolation>e){if(p==null)return;if(!number(p.get("x")))add(e,"REQUIRED",path+".x","finite JSON number",summary(p.get("x")));if(!number(p.get("y")))add(e,"REQUIRED",path+".y","finite JSON number",summary(p.get("y")));}
    private String safeText(ObjectNode p,String f,String path,int limit,List<RuleViolation>e){JsonNode n=p.get(f);String v=n!=null&&n.isTextual()?n.asText():null;if(!safeString(v,limit)){add(e,n==null?"REQUIRED":"TEXT",path+"."+f,"safe text 1.."+limit,summary(n));return null;}return v.strip();}
    private boolean safeString(String v,int limit){if(v==null)return false;String s=v.strip();if(s.isEmpty()||s.codePointCount(0,s.length())>limit||s.indexOf('<')>=0||s.indexOf('>')>=0)return false;return s.codePoints().noneMatch(c->c<32||c==127);}
    private void constantText(ObjectNode p,String f,String path,String expected,List<RuleViolation>e){JsonNode n=p.get(f);if(n==null||!n.isTextual()||!expected.equals(n.asText()))add(e,n==null?"REQUIRED":"CONST",path+"."+f,expected,summary(n));}
    private void enumText(ObjectNode p,String f,String path,Set<String>values,List<RuleViolation>e){JsonNode n=p.get(f);if(n==null||!n.isTextual()||!values.contains(n.asText()))add(e,n==null?"REQUIRED":"ENUM",path+"."+f,values.toString(),summary(n));}
    private void enumArray(ArrayNode a,String path,Set<String>values,List<RuleViolation>e){if(a==null)return;Set<String>seen=new HashSet<>();for(int i=0;i<a.size();i++){JsonNode n=a.get(i);if(!n.isTextual()||!values.contains(n.asText())||!seen.add(n.asText()))add(e,"ENUM",path+"["+i+"]","unique "+values,summary(n));}}
    private void resource(ObjectNode p,String f,String path,Set<String>keys,List<RuleViolation>e){JsonNode n=p.get(f);if(n==null||!n.isTextual()||!keys.contains(n.asText()))add(e,"RESOURCE_KEY_NOT_ALLOWED",path+"."+f,keys.toString(),summary(n));}
    private void color(ObjectNode p,String f,String path,List<RuleViolation>e){JsonNode n=p.get(f);if(n==null||!n.isTextual()||!COLOR.matcher(n.asText()).matches())add(e,n==null?"REQUIRED":"COLOR",path+"."+f,"#RRGGBB",summary(n));}
    private void uniqueId(ObjectNode p,String f,String path,Set<String>seen,List<RuleViolation>e){JsonNode n=p.get(f);String v=n!=null&&n.isTextual()?n.asText():null;if(v==null||!ID.matcher(v).matches()||!seen.add(v))add(e,"DUPLICATE_OR_MISSING_ID",path+"."+f,"unique Id",summary(n));}
    private JsonNode alias(ObjectNode p,String a,String b,String path,List<RuleViolation>e){if(p.has(a)&&p.has(b)){if(!p.get(a).equals(p.get(b)))add(e,"ALIAS_CONFLICT",path+"."+a,a+" equals "+b,summary(p.get(b)));return p.get(a);}return p.has(a)?p.get(a):p.get(b);}
    private String aliasText(ObjectNode p,String a,String b,String path,List<RuleViolation>e){JsonNode n=alias(p,a,b,path,e);return n!=null&&n.isTextual()?n.asText():null;}
    private Set<String> ids(ArrayNode a,String field){Set<String>out=new HashSet<>();for(JsonNode n:a)if(n.isObject()&&n.path(field).isTextual())out.add(n.path(field).asText());return out;}
    private Map<String,ObjectNode> index(ArrayNode a,String field){Map<String,ObjectNode>out=new HashMap<>();for(JsonNode n:a)if(n.isObject())out.put(n.path(field).asText(),(ObjectNode)n);return out;}
    private void circleArrayBounds(ArrayNode a,int w,int h,String path,List<RuleViolation>e){if(a==null)return;for(int i=0;i<a.size();i++)if(a.get(i).isObject()&&integerNode(a.get(i).get("size")))circleBounds((ObjectNode)a.get(i),a.get(i).path("size").intValue()/2.0,w,h,path+"["+i+"]",e);}
    private void rectArrayBounds(ArrayNode a,int w,int h,String path,List<RuleViolation>e){if(a==null)return;for(int i=0;i<a.size();i++)if(a.get(i).isObject())rectBounds((ObjectNode)a.get(i),w,h,path+"["+i+"]",e);}
    private void circleBounds(ObjectNode n,double r,int w,int h,String path,List<RuleViolation>e){if(!number(n.get("x"))||!number(n.get("y")))return;double x=n.path("x").doubleValue(),y=n.path("y").doubleValue();if(x-r<0||x+r>w||y-r<0||y+r>h)add(e,"WORLD_BOUNDS",path,"body inside world",x+","+y);}
    private void rectBounds(ObjectNode n,int w,int h,String path,List<RuleViolation>e){if(!number(n.get("x"))||!number(n.get("y"))||!integerNode(n.get("width"))||!integerNode(n.get("height")))return;double x=n.path("x").doubleValue(),y=n.path("y").doubleValue(),rw=n.path("width").intValue()/2.0,rh=n.path("height").intValue()/2.0;if(x-rw<0||x+rw>w||y-rh<0||y+rh>h)add(e,"WORLD_BOUNDS",path,"rectangle inside world",x+","+y);}
    private void overlapCircles(ObjectNode obstacle,ArrayNode values,String path,int obstacleIndex,List<RuleViolation>e){if(values==null)return;for(int i=0;i<values.size();i++)if(values.get(i).isObject()&&integerNode(values.get(i).get("size"))&&circleRectOverlap((ObjectNode)values.get(i),values.get(i).path("size").intValue()/2.0,obstacle))add(e,"WORLD_OVERLAP",path+"["+i+"]","entity outside obstacles","obstacle "+obstacleIndex);}
    private boolean circleRectOverlap(ObjectNode circle,double radius,ObjectNode rect){if(!number(circle.get("x"))||!number(circle.get("y"))||!number(rect.get("x"))||!number(rect.get("y"))||!integerNode(rect.get("width"))||!integerNode(rect.get("height")))return false;double left=rect.path("x").doubleValue()-rect.path("width").intValue()/2.0,right=rect.path("x").doubleValue()+rect.path("width").intValue()/2.0,top=rect.path("y").doubleValue()-rect.path("height").intValue()/2.0,bottom=rect.path("y").doubleValue()+rect.path("height").intValue()/2.0;double closestX=Math.max(left,Math.min(circle.path("x").doubleValue(),right)),closestY=Math.max(top,Math.min(circle.path("y").doubleValue(),bottom));double dx=circle.path("x").doubleValue()-closestX,dy=circle.path("y").doubleValue()-closestY;return dx*dx+dy*dy<radius*radius;}
    private boolean rectOverlap(ObjectNode a,ObjectNode b){if(!number(a.get("x"))||!number(a.get("y"))||!integerNode(a.get("width"))||!integerNode(a.get("height"))||!number(b.get("x"))||!number(b.get("y"))||!integerNode(b.get("width"))||!integerNode(b.get("height")))return false;return Math.abs(a.path("x").doubleValue()-b.path("x").doubleValue())*2<a.path("width").intValue()+b.path("width").intValue()&&Math.abs(a.path("y").doubleValue()-b.path("y").doubleValue())*2<a.path("height").intValue()+b.path("height").intValue();}
    private void copy(ObjectNode target,ObjectNode source,String...fields){for(String field:fields)target.set(field,source.get(field).deepCopy());}
    private int legacySeed(ObjectNode legacy){try{byte[]digest=MessageDigest.getInstance("SHA-256").digest(canonicalJson(sortObject(legacy)).getBytes(StandardCharsets.UTF_8));byte[]first={digest[0],digest[1],digest[2],digest[3]};return new BigInteger(1,first).and(BigInteger.valueOf(0x7fffffffL)).intValue();}catch(Exception e){throw new IllegalStateException(e);}}
    private String versionSummary(ObjectNode source){if(source.path("metadata").has("schemaVersion"))return source.path("metadata").path("schemaVersion").asText();return source.path("version").asText("missing");}
    private String summary(JsonNode n){if(n==null)return"missing";String s=n.toString();return s.length()>120?s.substring(0,120):s;}
    private void add(List<RuleViolation>e,String code,String path,String expected,String actual){e.add(new RuleViolation(code,path,"BLOCKING",expected,actual));}
}
