export const TELEMETRY_EVENTS = [
  "SESSION_STARTED", "ITEM_COLLECTED", "PLAYER_HIT", "GAME_WON", "GAME_LOST", "SESSION_RESTARTED", "SESSION_ENDED"
];

const RESOURCES = {
  player: new Set(["player.blue", "player.green"]),
  collectible: new Set(["collectible.gem", "collectible.artifact", "collectible.core"]),
  enemy: new Set(["enemy.guard", "enemy.drone"]),
  exit: new Set(["exit.portal", "exit.door"]),
  obstacle: new Set(["obstacle.stone", "obstacle.metal", "obstacle.wood"]),
  sound: new Set(["sfx.collect", "sfx.hit", "sfx.win", "sfx.lose", "sfx.silent"])
};
const COLORS = { floor: "#14213D", wall: "#24324A", player: "#5EEAD4", item: "#FACC15", enemy: "#FB7185", exit: "#22C55E" };
const ID = /^[a-z][a-z0-9-]{0,31}$/;
const COLOR = /^#[0-9a-fA-F]{6}$/;

export const defaultGameConfig = {
  metadata: { schemaVersion: "2.0", gameType: "arcade_collect", title: "博物馆夺宝", seed: 20260715 },
  viewport: { width: 960, height: 540, scaleMode: "fit" },
  world: {
    width: 960, height: 540, spawn: { x: 96, y: 96 },
    obstacles: [{ id: "wall-1", x: 360, y: 180, width: 160, height: 24, spriteKey: "obstacle.stone" }]
  },
  player: { speed: 220, size: 28, maxHealth: 3, hitInvulnerabilityMs: 1000, spriteKey: "player.blue" },
  entities: {
    collectibles: [
      { id: "item-1", x: 260, y: 140, size: 18, score: 100, label: "青铜藏品", spriteKey: "collectible.artifact" },
      { id: "item-2", x: 520, y: 300, size: 18, score: 100, label: "宝石藏品", spriteKey: "collectible.gem" }
    ],
    enemies: [{ id: "enemy-1", x: 620, y: 220, size: 28, speed: 90, spriteKey: "enemy.guard" }],
    exit: { x: 860, y: 450, width: 54, height: 72, label: "出口", spriteKey: "exit.door" }
  },
  behaviors: { enemyPatrols: [{ enemyId: "enemy-1", axis: "y", distance: 120 }], contact: { damage: 1 } },
  objectives: { targetCollectibles: 2, winCondition: "collect_target_then_exit", loseConditions: ["health_depleted", "time_expired"] },
  balance: { timeLimitSeconds: 90, winBonus: 500, difficulty: "normal" },
  presentation: {
    palette: { ...COLORS },
    audio: { collect: "sfx.collect", hit: "sfx.hit", win: "sfx.win", lose: "sfx.lose" },
    ui: { objective: "取得两件藏品，避开守卫并前往出口。", controls: "使用 WASD、方向键或触摸摇杆移动。" }
  },
  telemetry: { events: [...TELEMETRY_EVENTS] }
};

export function safeJsonParse(value) {
  if (!value) return null;
  if (typeof value === "object") return value;
  try { return JSON.parse(value); } catch { return null; }
}

export function extractGameConfig(source, depth = 0) {
  const value = safeJsonParse(source);
  if (!isObject(value) || depth > 4) return null;
  const wrapped = value.game_config ?? value.gameConfig ?? value.data ?? value.raw_result?.game_config ?? value.rawResult?.gameConfig;
  if (wrapped !== undefined) return depth === 4 ? null : extractGameConfig(wrapped, depth + 1);
  return value.metadata?.schemaVersion || value.version ? value : null;
}

export function extractGameConfigFromArtifacts(artifacts = []) {
  const preferred = artifacts.find((artifact) =>
    ["GAME_CONFIG_GENERATE_RESULT", "GAME_CONFIG", "PHASER_GAME_CONFIG"].includes(artifact.artifactType)
  );
  const candidates = preferred ? [preferred, ...artifacts] : artifacts;
  for (const artifact of candidates) {
    const config = extractGameConfig(artifact.content);
    if (config) return { config, artifact };
  }
  return { config: null, artifact: null };
}

export function validateGameConfig(rawConfig) {
  const source = extractGameConfig(rawConfig);
  if (!source) return invalid(error("JSON_OBJECT_REQUIRED", "$", "GameConfig must be one direct or registered wrapped JSON object"));
  const errors = [];
  let candidate = source;
  let migrated = false;
  if (source.metadata?.schemaVersion === "2.0") {
    candidate = structuredClone(source);
  } else if (source.version === "1.0") {
    migrated = true;
    candidate = migrateLegacyGameConfig(source, errors);
  } else {
    errors.push(error("UNSUPPORTED_SCHEMA_VERSION", "$", "Only GameConfig 2.0 or migratable 1.0 is supported"));
  }
  if (candidate) validateV2(candidate, errors);
  if (errors.length) return { valid: false, errors, config: null, migrated };
  return { valid: true, errors: [], config: canonicalizeV2(candidate), migrated };
}

export function normalizeGameConfig(rawConfig) {
  const result = validateGameConfig(rawConfig);
  if (!result.valid) throw new Error(result.errors.map(formatGameConfigError).join("; "));
  return result.config;
}

export function formatGameConfigError(value) {
  return `${value.code} ${value.path}: ${value.message}`;
}

function validateV2(root, errors) {
  unknown(root, "$", ["metadata", "viewport", "world", "player", "entities", "behaviors", "objectives", "balance", "presentation", "telemetry"], errors);
  const metadata = object(root, "metadata", "$", errors);
  const viewport = object(root, "viewport", "$", errors);
  const world = object(root, "world", "$", errors);
  const player = object(root, "player", "$", errors);
  const entities = object(root, "entities", "$", errors);
  const behaviors = object(root, "behaviors", "$", errors);
  const objectives = object(root, "objectives", "$", errors);
  const balance = object(root, "balance", "$", errors);
  const presentation = object(root, "presentation", "$", errors);
  const telemetry = object(root, "telemetry", "$", errors);

  if (metadata) {
    unknown(metadata, "$.metadata", ["schemaVersion", "gameType", "title", "seed"], errors);
    constant(metadata, "schemaVersion", "$.metadata", "2.0", errors);
    constant(metadata, "gameType", "$.metadata", "arcade_collect", errors);
    text(metadata, "title", "$.metadata", 80, errors);
    integer(metadata, "seed", "$.metadata", 0, 2147483647, errors);
  }
  if (viewport) {
    unknown(viewport, "$.viewport", ["width", "height", "scaleMode"], errors);
    integer(viewport, "width", "$.viewport", 640, 1280, errors);
    integer(viewport, "height", "$.viewport", 360, 720, errors);
    constant(viewport, "scaleMode", "$.viewport", "fit", errors);
    if (isInteger(viewport.width) && isInteger(viewport.height)
      && Math.abs(viewport.width / viewport.height - 16 / 9) / (16 / 9) > 0.01) {
      errors.push(error("VIEWPORT_RATIO", "$.viewport", "Viewport must be 16:9 within 1%"));
    }
  }
  let obstacles = null;
  if (world) {
    unknown(world, "$.world", ["width", "height", "spawn", "obstacles"], errors);
    integer(world, "width", "$.world", 640, 1280, errors); integer(world, "height", "$.world", 360, 720, errors);
    point(object(world, "spawn", "$.world", errors), "$.world.spawn", errors);
    obstacles = array(world, "obstacles", "$.world", 0, 16, errors);
    validateEntries(obstacles, "$.world.obstacles", ["id", "x", "y", "width", "height", "spriteKey"], errors, (entry, path) => {
      identifier(entry, "id", path, errors); point(entry, path, errors);
      integer(entry, "width", path, 24, 320, errors); integer(entry, "height", path, 24, 320, errors);
      resource(entry, "spriteKey", path, RESOURCES.obstacle, errors);
    });
  }
  if (viewport && world && (viewport.width !== world.width || viewport.height !== world.height)) {
    errors.push(error("WORLD_VIEWPORT_MISMATCH", "$.world", "World dimensions must equal viewport dimensions"));
  }
  if (player) {
    unknown(player, "$.player", ["speed", "size", "maxHealth", "hitInvulnerabilityMs", "spriteKey"], errors);
    integer(player, "speed", "$.player", 80, 400, errors); integer(player, "size", "$.player", 24, 64, errors);
    integer(player, "maxHealth", "$.player", 1, 5, errors); integer(player, "hitInvulnerabilityMs", "$.player", 0, 3000, errors);
    resource(player, "spriteKey", "$.player", RESOURCES.player, errors);
  }

  let collectibles = null; let enemies = null; let exit = null;
  if (entities) {
    unknown(entities, "$.entities", ["collectibles", "enemies", "exit"], errors);
    collectibles = array(entities, "collectibles", "$.entities", 1, 20, errors);
    enemies = array(entities, "enemies", "$.entities", 0, 12, errors);
    exit = object(entities, "exit", "$.entities", errors);
    validateEntries(collectibles, "$.entities.collectibles", ["id", "x", "y", "size", "score", "label", "spriteKey"], errors, (entry, path) => {
      identifier(entry, "id", path, errors); point(entry, path, errors); integer(entry, "size", path, 12, 48, errors);
      integer(entry, "score", path, 1, 1000, errors); text(entry, "label", path, 80, errors);
      resource(entry, "spriteKey", path, RESOURCES.collectible, errors);
    });
    validateEntries(enemies, "$.entities.enemies", ["id", "x", "y", "size", "speed", "spriteKey"], errors, (entry, path) => {
      identifier(entry, "id", path, errors); point(entry, path, errors); integer(entry, "size", path, 24, 64, errors);
      integer(entry, "speed", path, 20, 240, errors); resource(entry, "spriteKey", path, RESOURCES.enemy, errors);
    });
    if (exit) {
      unknown(exit, "$.entities.exit", ["x", "y", "width", "height", "label", "spriteKey"], errors);
      point(exit, "$.entities.exit", errors); integer(exit, "width", "$.entities.exit", 32, 160, errors);
      integer(exit, "height", "$.entities.exit", 32, 160, errors); text(exit, "label", "$.entities.exit", 80, errors);
      resource(exit, "spriteKey", "$.entities.exit", RESOURCES.exit, errors);
    }
  }
  let patrols = null;
  if (behaviors) {
    unknown(behaviors, "$.behaviors", ["enemyPatrols", "contact"], errors);
    patrols = array(behaviors, "enemyPatrols", "$.behaviors", 0, 12, errors);
    validateEntries(patrols, "$.behaviors.enemyPatrols", ["enemyId", "axis", "distance"], errors, (entry, path) => {
      identifier(entry, "enemyId", path, errors); enumValue(entry, "axis", path, ["x", "y"], errors);
      integer(entry, "distance", path, 32, 480, errors);
    }, "enemyId");
    const contact = object(behaviors, "contact", "$.behaviors", errors);
    if (contact) { unknown(contact, "$.behaviors.contact", ["damage"], errors); integer(contact, "damage", "$.behaviors.contact", 1, 5, errors); }
  }
  if (objectives) {
    unknown(objectives, "$.objectives", ["targetCollectibles", "winCondition", "loseConditions"], errors);
    integer(objectives, "targetCollectibles", "$.objectives", 1, 20, errors);
    constant(objectives, "winCondition", "$.objectives", "collect_target_then_exit", errors);
    const conditions = array(objectives, "loseConditions", "$.objectives", 1, 2, errors);
    uniqueEnumArray(conditions, "$.objectives.loseConditions", ["health_depleted", "time_expired"], errors);
    if (collectibles && objectives.targetCollectibles > collectibles.length) errors.push(error("TARGET_EXCEEDS_COLLECTIBLES", "$.objectives.targetCollectibles", "Target exceeds collectible count"));
  }
  if (balance) {
    unknown(balance, "$.balance", ["timeLimitSeconds", "winBonus", "difficulty"], errors);
    integer(balance, "timeLimitSeconds", "$.balance", 30, 600, errors); integer(balance, "winBonus", "$.balance", 0, 10000, errors);
    enumValue(balance, "difficulty", "$.balance", ["easy", "normal", "hard"], errors);
  }
  validatePresentation(presentation, errors); validateTelemetry(telemetry, errors);
  validateReferences(enemies, patrols, errors);
  if (world && player) validateGeometry(world, player, obstacles, collectibles, enemies, exit, patrols, errors);
}

function validatePresentation(value, errors) {
  if (!value) return;
  unknown(value, "$.presentation", ["palette", "audio", "ui"], errors);
  const palette = object(value, "palette", "$.presentation", errors);
  const audio = object(value, "audio", "$.presentation", errors);
  const ui = object(value, "ui", "$.presentation", errors);
  if (palette) {
    unknown(palette, "$.presentation.palette", ["floor", "wall", "player", "item", "enemy", "exit"], errors);
    for (const field of ["floor", "wall", "player", "item", "enemy", "exit"]) {
      if (typeof palette[field] !== "string" || !COLOR.test(palette[field])) errors.push(error("COLOR", `$.presentation.palette.${field}`, "Expected #RRGGBB"));
    }
  }
  if (audio) {
    unknown(audio, "$.presentation.audio", ["collect", "hit", "win", "lose"], errors);
    for (const field of ["collect", "hit", "win", "lose"]) resource(audio, field, "$.presentation.audio", RESOURCES.sound, errors);
  }
  if (ui) { unknown(ui, "$.presentation.ui", ["objective", "controls"], errors); text(ui, "objective", "$.presentation.ui", 160, errors); text(ui, "controls", "$.presentation.ui", 160, errors); }
}

function validateTelemetry(value, errors) {
  if (!value) return;
  unknown(value, "$.telemetry", ["events"], errors);
  const events = array(value, "events", "$.telemetry", 7, 7, errors);
  if (events && (new Set(events).size !== 7 || TELEMETRY_EVENTS.some((event) => !events.includes(event)))) {
    errors.push(error("TELEMETRY_EVENTS", "$.telemetry.events", "Expected the seven allowed events exactly once"));
  }
}

function validateReferences(enemies, patrols, errors) {
  if (!enemies || !patrols) return;
  const enemyIds = new Set(enemies.map((entry) => entry.id));
  const patrolIds = new Set(patrols.map((entry) => entry.enemyId));
  if (patrolIds.size !== patrols.length || enemyIds.size !== enemies.length || enemyIds.size !== patrolIds.size
    || [...enemyIds].some((id) => !patrolIds.has(id))) {
    errors.push(error("PATROL_REFERENCE", "$.behaviors.enemyPatrols", "Every enemy must have exactly one patrol"));
  }
}

function validateGeometry(world, player, obstacles, collectibles, enemies, exit, patrols, errors) {
  const { width, height } = world;
  if (![width, height].every(isInteger)) return;
  circleBounds(world.spawn, player.size / 2, width, height, "$.world.spawn", errors);
  obstacles?.forEach((entry, index) => rectBounds(entry, width, height, `$.world.obstacles[${index}]`, errors));
  collectibles?.forEach((entry, index) => circleBounds(entry, entry.size / 2, width, height, `$.entities.collectibles[${index}]`, errors));
  enemies?.forEach((entry, index) => circleBounds(entry, entry.size / 2, width, height, `$.entities.enemies[${index}]`, errors));
  if (exit) rectBounds(exit, width, height, "$.entities.exit", errors);
  const enemyById = new Map(enemies?.map((entry) => [entry.id, entry]) || []);
  patrols?.forEach((patrol, index) => {
    const enemy = enemyById.get(patrol.enemyId);
    if (!enemy || !isInteger(patrol.distance) || !isInteger(enemy.size)) return;
    const center = patrol.axis === "y" ? enemy.y : enemy.x;
    const limit = patrol.axis === "y" ? height : width;
    const radius = enemy.size / 2;
    if (center - patrol.distance - radius < 0 || center + patrol.distance + radius > limit) {
      errors.push(error("WORLD_BOUNDS", `$.behaviors.enemyPatrols[${index}].distance`, "Patrol body must remain inside world"));
    }
  });
  obstacles?.forEach((obstacle, obstacleIndex) => {
    if (circleRectOverlap(world.spawn, player.size / 2, obstacle)) errors.push(error("WORLD_OVERLAP", "$.world.spawn", `Spawn overlaps obstacle ${obstacleIndex}`));
    collectibles?.forEach((entry, index) => { if (circleRectOverlap(entry, entry.size / 2, obstacle)) errors.push(error("WORLD_OVERLAP", `$.entities.collectibles[${index}]`, `Collectible overlaps obstacle ${obstacleIndex}`)); });
    enemies?.forEach((entry, index) => { if (circleRectOverlap(entry, entry.size / 2, obstacle)) errors.push(error("WORLD_OVERLAP", `$.entities.enemies[${index}]`, `Enemy overlaps obstacle ${obstacleIndex}`)); });
    if (exit && rectOverlap(obstacle, exit)) errors.push(error("WORLD_OVERLAP", "$.entities.exit", `Exit overlaps obstacle ${obstacleIndex}`));
  });
}

export function migrateLegacyGameConfig(source, errors = []) {
  if (!isObject(source)) { errors.push(error("JSON_OBJECT_REQUIRED", "$", "Legacy GameConfig must be an object")); return null; }
  unknown(source, "$", ["version", "title", "gameType", "game_type", "world", "theme", "player", "obstacles", "items", "collectibles", "enemies", "exit", "rules", "ui"], errors);
  constant(source, "version", "$", "1.0", errors);
  const gameType = alias(source, "gameType", "game_type", "$", errors);
  if (gameType !== "top_down_collect") errors.push(error("UNSUPPORTED_GAME_TYPE", "$.gameType", "Legacy gameType must be top_down_collect"));
  text(source, "title", "$", 80, errors);
  const world = object(source, "world", "$", errors); const player = object(source, "player", "$", errors);
  const items = aliasArray(source, "items", "collectibles", "$", 1, 20, errors);
  const enemies = array(source, "enemies", "$", 0, 12, errors); const exit = object(source, "exit", "$", errors);
  const rules = object(source, "rules", "$", errors); const ui = object(source, "ui", "$", errors);
  const obstacles = source.obstacles === undefined ? [] : array(source, "obstacles", "$", 0, 16, errors);
  if (world) { unknown(world, "$.world", ["width", "height", "backgroundColor"], errors); integer(world, "width", "$.world", 640, 1280, errors); integer(world, "height", "$.world", 360, 720, errors); if (world.backgroundColor !== undefined && !COLOR.test(world.backgroundColor)) errors.push(error("COLOR", "$.world.backgroundColor", "Expected #RRGGBB")); }
  if (player) { unknown(player, "$.player", ["x", "y", "speed", "size"], errors); point(player, "$.player", errors); integer(player, "speed", "$.player", 80, 400, errors); optionalInteger(player, "size", "$.player", 24, 64, errors); }
  validateEntries(obstacles, "$.obstacles", ["id", "x", "y", "width", "height"], errors, (entry, path) => { identifier(entry, "id", path, errors); point(entry, path, errors); integer(entry, "width", path, 24, 320, errors); integer(entry, "height", path, 24, 320, errors); });
  validateEntries(items, "$.items", ["id", "x", "y", "size", "label"], errors, (entry, path) => { identifier(entry, "id", path, errors); point(entry, path, errors); optionalInteger(entry, "size", path, 12, 48, errors); if (entry.label !== undefined) text(entry, "label", path, 80, errors); });
  validateEntries(enemies, "$.enemies", ["id", "x", "y", "size", "speed", "axis", "patrolAxis", "range", "patrolDistance"], errors, (entry, path) => {
    identifier(entry, "id", path, errors); point(entry, path, errors); optionalInteger(entry, "size", path, 24, 64, errors); integer(entry, "speed", path, 20, 240, errors);
    const axis = alias(entry, "axis", "patrolAxis", path, errors); if (!["x", "y"].includes(axis)) errors.push(error("ENUM", `${path}.axis`, "Expected x or y"));
    const distance = alias(entry, "range", "patrolDistance", path, errors); if (!isInteger(distance) || distance < 32 || distance > 480) errors.push(error("RANGE", `${path}.range`, "Expected integer 32..480"));
  });
  if (exit) { unknown(exit, "$.exit", ["x", "y", "width", "height", "label"], errors); point(exit, "$.exit", errors); optionalInteger(exit, "width", "$.exit", 32, 160, errors); optionalInteger(exit, "height", "$.exit", 32, 160, errors); if (exit.label !== undefined) text(exit, "label", "$.exit", 80, errors); }
  if (rules) { unknown(rules, "$.rules", ["targetItems", "winCondition", "loseCondition"], errors); integer(rules, "targetItems", "$.rules", 1, 20, errors); constant(rules, "winCondition", "$.rules", "collect_all_then_exit", errors); constant(rules, "loseCondition", "$.rules", "touch_enemy", errors); }
  if (ui) { unknown(ui, "$.ui", ["objective", "controls", "controlHint"], errors); text(ui, "objective", "$.ui", 160, errors); const controls = alias(ui, "controls", "controlHint", "$.ui", errors); if (!safeText(controls, 160)) errors.push(error("TEXT", "$.ui.controls", "Expected safe text")); }
  let palette = null;
  if (source.theme !== undefined) { const theme = object(source, "theme", "$", errors); if (theme) { unknown(theme, "$.theme", ["palette"], errors); palette = object(theme, "palette", "$.theme", errors); if (palette) { unknown(palette, "$.theme.palette", ["floor", "wall", "player", "item", "enemy", "exit"], errors); for (const field of Object.keys(COLORS)) if (palette[field] !== undefined && !COLOR.test(palette[field])) errors.push(error("COLOR", `$.theme.palette.${field}`, "Expected #RRGGBB")); } } }
  if (errors.length) return null;

  const migratedPalette = Object.fromEntries(Object.keys(COLORS).map((field) => [field,
    palette?.[field] || (field === "floor" && COLOR.test(world.backgroundColor || "") ? world.backgroundColor : COLORS[field])
  ]));
  return {
    metadata: { schemaVersion: "2.0", gameType: "arcade_collect", title: source.title.trim(), seed: parseInt(sha256Hex(canonicalStringify(source)).slice(0, 8), 16) & 0x7fffffff },
    viewport: { width: world.width, height: world.height, scaleMode: "fit" },
    world: { width: world.width, height: world.height, spawn: { x: player.x, y: player.y }, obstacles: obstacles.map((entry) => ({ ...entry, spriteKey: "obstacle.stone" })) },
    player: { speed: player.speed, size: player.size ?? 28, maxHealth: 1, hitInvulnerabilityMs: 0, spriteKey: "player.blue" },
    entities: {
      collectibles: items.map((entry, index) => ({ id: entry.id, x: entry.x, y: entry.y, size: entry.size ?? 18, score: 100, label: entry.label ?? `目标 ${index + 1}`, spriteKey: "collectible.gem" })),
      enemies: enemies.map((entry) => ({ id: entry.id, x: entry.x, y: entry.y, size: entry.size ?? 28, speed: entry.speed, spriteKey: "enemy.guard" })),
      exit: { x: exit.x, y: exit.y, width: exit.width ?? 54, height: exit.height ?? 72, label: exit.label ?? "EXIT", spriteKey: "exit.door" }
    },
    behaviors: {
      enemyPatrols: enemies.map((entry) => ({ enemyId: entry.id, axis: alias(entry, "axis", "patrolAxis", "$.enemies", []), distance: alias(entry, "range", "patrolDistance", "$.enemies", []) })),
      contact: { damage: 1 }
    },
    objectives: { targetCollectibles: rules.targetItems, winCondition: "collect_target_then_exit", loseConditions: ["health_depleted"] },
    balance: { timeLimitSeconds: 90, winBonus: 500, difficulty: "normal" },
    presentation: { palette: migratedPalette, audio: { collect: "sfx.collect", hit: "sfx.hit", win: "sfx.win", lose: "sfx.lose" }, ui: { objective: ui.objective.trim(), controls: alias(ui, "controls", "controlHint", "$.ui", []).trim() } },
    telemetry: { events: [...TELEMETRY_EVENTS] }
  };
}

function canonicalizeV2(value) {
  const copy = structuredClone(value);
  for (const key of Object.keys(copy.presentation.palette)) copy.presentation.palette[key] = copy.presentation.palette[key].toUpperCase();
  copy.objectives.loseConditions.sort((a, b) => ["health_depleted", "time_expired"].indexOf(a) - ["health_depleted", "time_expired"].indexOf(b));
  copy.telemetry.events = [...TELEMETRY_EVENTS];
  return copy;
}

function validateEntries(values, path, allowed, errors, validate, idField = "id") {
  if (!values) return;
  const seen = new Set();
  values.forEach((entry, index) => {
    const entryPath = `${path}[${index}]`;
    if (!isObject(entry)) { errors.push(error("TYPE", entryPath, "Expected object")); return; }
    unknown(entry, entryPath, allowed, errors); validate(entry, entryPath);
    if (typeof entry[idField] === "string" && seen.has(entry[idField])) errors.push(error("DUPLICATE_OR_MISSING_ID", `${entryPath}.${idField}`, "ID must be unique"));
    seen.add(entry[idField]);
  });
}
function object(parent, field, path, errors) { const value = parent?.[field]; if (!isObject(value)) { errors.push(error("REQUIRED", `${path}.${field}`, "Expected required object")); return null; } return value; }
function array(parent, field, path, min, max, errors) { const value = parent?.[field]; if (!Array.isArray(value)) { errors.push(error("REQUIRED", `${path}.${field}`, "Expected required array")); return null; } if (value.length < min || value.length > max) errors.push(error("ARRAY_SIZE", `${path}.${field}`, `Expected ${min}..${max} entries`)); return value; }
function aliasArray(parent, primary, secondary, path, min, max, errors) { const value = alias(parent, primary, secondary, path, errors); if (!Array.isArray(value)) { errors.push(error("REQUIRED", `${path}.${primary}`, "Expected required array")); return null; } if (value.length < min || value.length > max) errors.push(error("ARRAY_SIZE", `${path}.${primary}`, `Expected ${min}..${max} entries`)); return value; }
function alias(parent, primary, secondary, path, errors) { if (parent[primary] !== undefined && parent[secondary] !== undefined && JSON.stringify(parent[primary]) !== JSON.stringify(parent[secondary])) errors.push(error("ALIAS_CONFLICT", `${path}.${primary}`, `${primary} conflicts with ${secondary}`)); return parent[primary] ?? parent[secondary]; }
function unknown(value, path, allowed, errors) { for (const field of Object.keys(value || {})) if (!allowed.includes(field)) errors.push(error("UNKNOWN_FIELD", `${path}.${field}`, "Unknown field is forbidden")); }
function constant(parent, field, path, expected, errors) { if (parent?.[field] !== expected) errors.push(error(parent?.[field] === undefined ? "REQUIRED" : "CONST", `${path}.${field}`, `Expected ${expected}`)); }
function integer(parent, field, path, min, max, errors) { const value = parent?.[field]; if (!isInteger(value) || value < min || value > max) errors.push(error(value === undefined ? "REQUIRED" : "RANGE", `${path}.${field}`, `Expected integer ${min}..${max}`)); }
function optionalInteger(parent, field, path, min, max, errors) { if (parent?.[field] !== undefined) integer(parent, field, path, min, max, errors); }
function point(value, path, errors) { if (!isNumber(value?.x)) errors.push(error("REQUIRED", `${path}.x`, "Expected finite JSON number")); if (!isNumber(value?.y)) errors.push(error("REQUIRED", `${path}.y`, "Expected finite JSON number")); }
function identifier(parent, field, path, errors) { if (typeof parent?.[field] !== "string" || !ID.test(parent[field])) errors.push(error("DUPLICATE_OR_MISSING_ID", `${path}.${field}`, "Expected Id")); }
function text(parent, field, path, limit, errors) { if (!safeText(parent?.[field], limit)) errors.push(error(parent?.[field] === undefined ? "REQUIRED" : "TEXT", `${path}.${field}`, `Expected safe text 1..${limit}`)); }
function safeText(value, limit) { const trimmed = typeof value === "string" ? value.trim() : ""; return Boolean(trimmed) && [...trimmed].length <= limit && !/[\u0000-\u001f\u007f<>]/.test(trimmed); }
function enumValue(parent, field, path, allowed, errors) { if (!allowed.includes(parent?.[field])) errors.push(error(parent?.[field] === undefined ? "REQUIRED" : "ENUM", `${path}.${field}`, `Expected ${allowed.join(" or ")}`)); }
function uniqueEnumArray(values, path, allowed, errors) { if (!values) return; const seen = new Set(); values.forEach((value, index) => { if (!allowed.includes(value) || seen.has(value)) errors.push(error("ENUM", `${path}[${index}]`, `Expected unique ${allowed.join(" or ")}`)); seen.add(value); }); }
function resource(parent, field, path, allowed, errors) { if (typeof parent?.[field] !== "string" || !allowed.has(parent[field])) errors.push(error("RESOURCE_KEY_NOT_ALLOWED", `${path}.${field}`, "Resource key is not allowed")); }
function circleBounds(value, radius, width, height, path, errors) { if (![value?.x, value?.y, radius].every(isNumber)) return; if (value.x - radius < 0 || value.x + radius > width || value.y - radius < 0 || value.y + radius > height) errors.push(error("WORLD_BOUNDS", path, "Body must remain inside world")); }
function rectBounds(value, width, height, path, errors) { if (![value?.x, value?.y, value?.width, value?.height].every(isNumber)) return; if (value.x - value.width / 2 < 0 || value.x + value.width / 2 > width || value.y - value.height / 2 < 0 || value.y + value.height / 2 > height) errors.push(error("WORLD_BOUNDS", path, "Rectangle must remain inside world")); }
function circleRectOverlap(circle, radius, rect) { if (![circle?.x, circle?.y, radius, rect?.x, rect?.y, rect?.width, rect?.height].every(isNumber)) return false; const closestX = Math.max(rect.x - rect.width / 2, Math.min(circle.x, rect.x + rect.width / 2)); const closestY = Math.max(rect.y - rect.height / 2, Math.min(circle.y, rect.y + rect.height / 2)); return (circle.x - closestX) ** 2 + (circle.y - closestY) ** 2 < radius ** 2; }
function rectOverlap(a, b) { return Math.abs(a.x - b.x) * 2 < a.width + b.width && Math.abs(a.y - b.y) * 2 < a.height + b.height; }
function isObject(value) { return value && typeof value === "object" && !Array.isArray(value); }
function isNumber(value) { return typeof value === "number" && Number.isFinite(value); }
function isInteger(value) { return Number.isInteger(value); }
function error(code, path, message) { return { code, path, message, severity: "BLOCKING" }; }
function invalid(...errors) { return { valid: false, errors, config: null, migrated: false }; }
function canonicalStringify(value) { if (Array.isArray(value)) return `[${value.map(canonicalStringify).join(",")}]`; if (isObject(value)) return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalStringify(value[key])}`).join(",")}}`; return JSON.stringify(value); }

// Synchronous SHA-256 keeps legacy migration deterministic in both browsers and Node.
function sha256Hex(text) {
  const rightRotate = (value, amount) => (value >>> amount) | (value << (32 - amount));
  const maxWord = 2 ** 32; const words = []; const hash = []; const k = []; let primeCounter = 0;
  const isComposite = {}; for (let candidate = 2; primeCounter < 64; candidate++) { if (!isComposite[candidate]) { for (let i = 0; i < 313; i += candidate) isComposite[i] = candidate; hash[primeCounter] = (candidate ** 0.5 * maxWord) | 0; k[primeCounter++] = (candidate ** (1 / 3) * maxWord) | 0; } }
  const bytes = new TextEncoder().encode(text); const bitLength = bytes.length * 8; const data = [...bytes, 0x80];
  while ((data.length % 64) !== 56) data.push(0); for (let i = 7; i >= 0; i--) data.push(i >= 4 ? 0 : (bitLength >>> (i * 8)) & 255);
  for (let offset = 0; offset < data.length; offset += 64) {
    for (let i = 0; i < 16; i++) words[i] = (data[offset + i * 4] << 24) | (data[offset + i * 4 + 1] << 16) | (data[offset + i * 4 + 2] << 8) | data[offset + i * 4 + 3];
    for (let i = 16; i < 64; i++) { const w15 = words[i - 15], w2 = words[i - 2]; words[i] = (words[i - 16] + (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3)) + words[i - 7] + (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))) | 0; }
    const old = hash.slice(0, 8); const work = old.slice();
    for (let i = 0; i < 64; i++) { const s1 = rightRotate(work[4], 6) ^ rightRotate(work[4], 11) ^ rightRotate(work[4], 25); const ch = (work[4] & work[5]) ^ (~work[4] & work[6]); const temp1 = (work[7] + s1 + ch + k[i] + words[i]) | 0; const s0 = rightRotate(work[0], 2) ^ rightRotate(work[0], 13) ^ rightRotate(work[0], 22); const maj = (work[0] & work[1]) ^ (work[0] & work[2]) ^ (work[1] & work[2]); const temp2 = (s0 + maj) | 0; work.pop(); work.unshift((temp1 + temp2) | 0); work[4] = (work[4] + temp1) | 0; }
    for (let i = 0; i < 8; i++) hash[i] = (old[i] + work[i]) | 0;
  }
  return hash.slice(0, 8).map((value) => (value >>> 0).toString(16).padStart(8, "0")).join("");
}

export function artifactText(artifact) {
  if (!artifact?.content) return "暂无内容";
  const parsed = safeJsonParse(artifact.content);
  if (!parsed) return artifact.content;
  return parsed.content || parsed.summary || parsed.text || JSON.stringify(parsed, null, 2);
}
