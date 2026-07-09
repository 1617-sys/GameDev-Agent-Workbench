import assert from "node:assert/strict";
import test from "node:test";

import {
  defaultGameConfig,
  extractGameConfig,
  extractGameConfigFromArtifacts,
  normalizeGameConfig,
  validateGameConfig
} from "../src/game/gameConfig.js";

function validConfig(overrides = {}) {
  return {
    ...defaultGameConfig,
    ...overrides,
    world: {
      ...defaultGameConfig.world,
      ...(overrides.world || {})
    },
    player: {
      ...defaultGameConfig.player,
      ...(overrides.player || {})
    },
    exit: {
      ...defaultGameConfig.exit,
      ...(overrides.exit || {})
    },
    rules: {
      ...defaultGameConfig.rules,
      ...(overrides.rules || {})
    },
    ui: {
      ...defaultGameConfig.ui,
      ...(overrides.ui || {})
    }
  };
}

test("default GameConfig matches the runtime contract", () => {
  const result = validateGameConfig(defaultGameConfig);

  assert.equal(result.valid, true);
  assert.equal(result.config.gameType, "top_down_collect");
  assert.equal(result.config.items.length, defaultGameConfig.items.length);
});

test("extracts GameConfig from JSON strings and supported wrappers", () => {
  const config = validConfig({ title: "Wrapped config" });

  assert.equal(extractGameConfig(JSON.stringify(config)).title, "Wrapped config");
  assert.equal(extractGameConfig({ game_config: config }).title, "Wrapped config");
  assert.equal(extractGameConfig({ gameConfig: config }).title, "Wrapped config");
  assert.equal(extractGameConfig({ data: config }).title, "Wrapped config");
  assert.equal(extractGameConfig({ raw_result: { game_config: config } }).title, "Wrapped config");
  assert.equal(extractGameConfig({ rawResult: { gameConfig: config } }).title, "Wrapped config");
});

test("artifact extraction prefers GameConfig artifact types", () => {
  const textArtifact = {
    artifactType: "GAME_CONCEPT",
    content: JSON.stringify({ content: "not a config" })
  };
  const configArtifact = {
    artifactType: "GAME_CONFIG_GENERATE_RESULT",
    content: JSON.stringify(validConfig({ title: "Artifact config" }))
  };

  const result = extractGameConfigFromArtifacts([textArtifact, configArtifact]);

  assert.equal(result.config.title, "Artifact config");
  assert.equal(result.artifact, configArtifact);
});

test("rejects invalid JSON without throwing", () => {
  assert.equal(extractGameConfig("{bad json"), null);

  const result = validateGameConfig("{bad json");

  assert.equal(result.valid, false);
  assert.equal(result.config, null);
});

test("rejects unsupported game types", () => {
  const result = validateGameConfig(validConfig({ gameType: "platformer" }));

  assert.equal(result.valid, false);
  assert.match(result.errors.join("\n"), /unsupported gameType/);
});

test("rejects invalid numeric runtime fields", () => {
  const cases = [
    validConfig({ world: { width: "wide" } }),
    validConfig({ player: { x: "left" } }),
    validConfig({ exit: { y: "bottom" } })
  ];

  for (const config of cases) {
    assert.equal(validateGameConfig(config).valid, false);
  }
});

test("rejects invalid collection fields", () => {
  assert.equal(validateGameConfig(validConfig({ items: "gems" })).valid, false);
  assert.equal(validateGameConfig(validConfig({ enemies: "guards" })).valid, false);
});

test("does not allow missing required structures to pass through defaults", () => {
  assert.equal(validateGameConfig({}).valid, false);
  assert.equal(validateGameConfig({ version: "1.0", title: "Too small" }).valid, false);
  assert.equal(validateGameConfig({ gameType: "top_down_collect", world: {} }).valid, false);
});

test("normalizes confirmed historical aliases", () => {
  const config = validConfig({
    gameType: undefined,
    game_type: "top_down_collect",
    items: undefined,
    collectibles: [{ id: "gem-alias", x: 1, y: 2 }]
  });

  const result = validateGameConfig(config);
  const normalized = normalizeGameConfig(config);

  assert.equal(result.valid, true);
  assert.equal(normalized.gameType, "top_down_collect");
  assert.equal(normalized.items[0].id, "gem-alias");
});
