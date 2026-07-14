import test from "node:test";
import assert from "node:assert/strict";
import { defaultGameConfig, extractGameConfig, normalizeGameConfig, validateGameConfig } from "../src/features/demo/runtime/gameConfig.js";

test("default GameConfig remains playable", () => {
  const result = validateGameConfig(defaultGameConfig);
  assert.equal(result.valid, true);
  assert.equal(result.config.gameType, "top_down_collect");
});

test("extracts wrapped game configuration", () => {
  assert.deepEqual(extractGameConfig({ game_config: defaultGameConfig }), defaultGameConfig);
  assert.deepEqual(extractGameConfig(JSON.stringify({ data: defaultGameConfig })), defaultGameConfig);
});

test("rejects missing required runtime structures before defaults", () => {
  const result = validateGameConfig({ version: "1.0", title: "Incomplete" });
  assert.equal(result.valid, false);
  assert.ok(result.errors.some((error) => error.includes("missing gameType")));
  assert.ok(result.errors.some((error) => error.includes("missing world")));
});

test("normalizes supported collectibles alias", () => {
  const config = normalizeGameConfig({ ...defaultGameConfig, items: undefined, collectibles: [{ id: "one", x: 20, y: 20 }] });
  assert.equal(config.items[0].id, "one");
});

test("keeps agent-authored obstacle layout and patrol axes", () => {
  const config = normalizeGameConfig({
    ...defaultGameConfig,
    obstacles: [{ id: "desk", x: 300, y: 200, width: 120, height: 30 }],
    enemies: [{ id: "guard", x: 500, y: 260, size: 30, speed: 80, range: 110, axis: "y" }]
  });
  assert.equal(config.obstacles[0].id, "desk");
  assert.equal(config.enemies[0].axis, "y");
});
