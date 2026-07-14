export const defaultGameConfig = {
  version: "1.0",
  title: "像素地牢 Demo",
  gameType: "top_down_collect",
  world: {
    width: 960,
    height: 540,
    backgroundColor: "#101827"
  },
  theme: {
    palette: {
      floor: "#14213d",
      wall: "#24324a",
      player: "#5eead4",
      item: "#facc15",
      enemy: "#fb7185",
      exit: "#22c55e"
    }
  },
  player: {
    x: 120,
    y: 260,
    size: 28,
    speed: 210,
    color: "#5eead4"
  },
  obstacles: [
    { id: "wall-1", x: 350, y: 120, width: 150, height: 24 },
    { id: "wall-2", x: 330, y: 390, width: 180, height: 24 },
    { id: "wall-3", x: 620, y: 270, width: 24, height: 150 },
    { id: "wall-4", x: 770, y: 430, width: 130, height: 24 }
  ],
  items: [
    { id: "gem-1", x: 260, y: 150, size: 18, label: "宝石" },
    { id: "gem-2", x: 520, y: 340, size: 18, label: "宝石" },
    { id: "gem-3", x: 740, y: 180, size: 18, label: "宝石" }
  ],
  enemies: [
    { id: "enemy-1", x: 420, y: 220, size: 28, speed: 90, range: 150, axis: "x" },
    { id: "enemy-2", x: 700, y: 380, size: 28, speed: 120, range: 120, axis: "y" }
  ],
  exit: {
    x: 860,
    y: 270,
    width: 54,
    height: 72,
    label: "出口"
  },
  rules: {
    targetItems: 3,
    winCondition: "collect_all_then_exit",
    loseCondition: "touch_enemy"
  },
  ui: {
    objective: "收集全部宝石，然后抵达出口。",
    controls: "WASD / 方向键移动，R 重新开始。"
  }
};

export function safeJsonParse(value) {
  if (!value) {
    return null;
  }
  if (typeof value === "object") {
    return value;
  }
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

export function extractGameConfig(source) {
  const value = safeJsonParse(source);
  if (!value) {
    return null;
  }

  if (value.game_config) return extractGameConfig(value.game_config);
  if (value.gameConfig) return extractGameConfig(value.gameConfig);
  if (value.data) return extractGameConfig(value.data);
  if (value.raw_result?.game_config) return extractGameConfig(value.raw_result.game_config);
  if (value.rawResult?.gameConfig) return extractGameConfig(value.rawResult.gameConfig);

  if (value.version || value.world || value.player || value.rules) {
    return value;
  }

  return null;
}

function requiredObject(value) {
  return value && typeof value === "object" && !Array.isArray(value);
}

function requiredNumber(value) {
  return Number.isFinite(Number(value));
}

function rawContractShape(rawConfig) {
  const source = extractGameConfig(rawConfig);
  if (!requiredObject(source)) {
    return {
      source: null,
      errors: ["GameConfig must be a JSON object with required runtime fields"]
    };
  }

  const errors = [];
  const gameType = source.gameType || source.game_type;
  const items = source.items || source.collectibles;

  if (!source.version) errors.push("missing version");
  if (!source.title) errors.push("missing title");
  if (!gameType) errors.push("missing gameType");
  if (gameType && gameType !== "top_down_collect") errors.push("unsupported gameType");
  if (!requiredObject(source.world)) errors.push("missing world");
  if (!requiredObject(source.player)) errors.push("missing player");
  if (!Array.isArray(items)) errors.push("items must be an array");
  if (!Array.isArray(source.enemies)) errors.push("enemies must be an array");
  if (source.obstacles !== undefined && !Array.isArray(source.obstacles)) errors.push("obstacles must be an array");
  if (!requiredObject(source.exit)) errors.push("missing exit");
  if (!requiredObject(source.rules)) errors.push("missing rules");
  if (!requiredObject(source.ui)) errors.push("missing ui");

  if (requiredObject(source.world) && (!requiredNumber(source.world.width) || !requiredNumber(source.world.height))) {
    errors.push("world.width / world.height must be numbers");
  }
  if (requiredObject(source.player) && (!requiredNumber(source.player.x) || !requiredNumber(source.player.y))) {
    errors.push("player.x / player.y must be numbers");
  }
  if (requiredObject(source.exit) && (!requiredNumber(source.exit.x) || !requiredNumber(source.exit.y))) {
    errors.push("exit.x / exit.y must be numbers");
  }

  return {
    source,
    errors
  };
}

export function extractGameConfigFromArtifacts(artifacts = []) {
  const preferred = artifacts.find((artifact) =>
    ["GAME_CONFIG_GENERATE_RESULT", "GAME_CONFIG", "PHASER_GAME_CONFIG"].includes(artifact.artifactType)
  );
  const candidates = preferred ? [preferred, ...artifacts] : artifacts;

  for (const artifact of candidates) {
    const config = extractGameConfig(artifact.content);
    if (config) {
      return {
        config,
        artifact
      };
    }
  }
  return {
    config: null,
    artifact: null
  };
}

export function normalizeGameConfig(rawConfig) {
  const source = extractGameConfig(rawConfig) || rawConfig || {};
  const items = source.items || source.collectibles || defaultGameConfig.items;
  const enemies = source.enemies || defaultGameConfig.enemies;
  const obstacles = Array.isArray(source.obstacles) ? source.obstacles : defaultGameConfig.obstacles;
  const world = { ...defaultGameConfig.world, ...(source.world || {}) };
  const theme = {
    ...defaultGameConfig.theme,
    ...(source.theme || {}),
    palette: {
      ...defaultGameConfig.theme.palette,
      ...(source.theme?.palette || {})
    }
  };

  return {
    ...defaultGameConfig,
    ...source,
    version: source.version || defaultGameConfig.version,
    title: source.title || defaultGameConfig.title,
    gameType: source.gameType || source.game_type || defaultGameConfig.gameType,
    world,
    theme,
    player: { ...defaultGameConfig.player, ...(source.player || {}) },
    obstacles,
    items,
    enemies,
    exit: { ...defaultGameConfig.exit, ...(source.exit || {}) },
    rules: { ...defaultGameConfig.rules, ...(source.rules || {}) },
    ui: { ...defaultGameConfig.ui, ...(source.ui || {}) }
  };
}

export function validateGameConfig(rawConfig) {
  const contract = rawContractShape(rawConfig);
  if (!contract.source) {
    return {
      valid: false,
      errors: contract.errors,
      config: null
    };
  }

  const errors = [...contract.errors];
  const config = normalizeGameConfig(contract.source);

  if (!config.version) errors.push("缺少 version");
  if (!config.title) errors.push("缺少 title");
  if (!config.gameType) errors.push("缺少 gameType");
  if (!Number.isFinite(Number(config.world?.width)) || !Number.isFinite(Number(config.world?.height))) {
    errors.push("world.width / world.height 必须是数字");
  }
  if (!config.player || !Number.isFinite(Number(config.player.x)) || !Number.isFinite(Number(config.player.y))) {
    errors.push("player.x / player.y 必须是数字");
  }
  if (!Array.isArray(config.items)) errors.push("items 必须是数组");
  if (!Array.isArray(config.enemies)) errors.push("enemies 必须是数组");
  if (!config.exit || !Number.isFinite(Number(config.exit.x)) || !Number.isFinite(Number(config.exit.y))) {
    errors.push("exit.x / exit.y 必须是数字");
  }
  if (!config.rules) errors.push("缺少 rules");
  if (!config.ui) errors.push("缺少 ui");

  return {
    valid: errors.length === 0,
    errors,
    config
  };
}

export function artifactText(artifact) {
  if (!artifact?.content) return "暂无内容";
  const parsed = safeJsonParse(artifact.content);
  if (!parsed) return artifact.content;
  return parsed.content || parsed.summary || parsed.text || JSON.stringify(parsed, null, 2);
}
