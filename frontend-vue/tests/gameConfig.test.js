import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  defaultGameConfig,
  extractGameConfig,
  migrateLegacyGameConfig,
  normalizeGameConfig,
  validateGameConfig
} from "../src/features/demo/runtime/gameConfig.js";

const fixture = (name) => JSON.parse(readFileSync(new URL(`../../docs/requirements/v3/examples/game-config-2.0/${name}`, import.meta.url), "utf8"));

test("default config is mechanically synchronized with the authoritative fixture", () => {
  const documented = fixture("valid-minimal.json");
  assert.deepEqual(defaultGameConfig, documented);
  const result = validateGameConfig(documented);
  assert.equal(result.valid, true);
  assert.equal(result.config.metadata.gameType, "arcade_collect");
});

test("validates reactive proxy inputs without mutating or defaulting them", () => {
  const documented = fixture("valid-minimal.json");
  const reactiveLike = new Proxy(documented, {});
  const result = validateGameConfig(reactiveLike);
  assert.equal(result.valid, true);
  assert.notEqual(result.config, documented);
  assert.deepEqual(result.config, documented);
});

test("extracts only registered wrappers up to four levels", () => {
  assert.deepEqual(extractGameConfig({ game_config: defaultGameConfig }), defaultGameConfig);
  assert.deepEqual(extractGameConfig(JSON.stringify({ data: defaultGameConfig })), defaultGameConfig);
  assert.equal(extractGameConfig({ data: { data: { data: { data: { data: defaultGameConfig } } } } }), null);
});

test("rejects documented missing, remote resource, and world-bound failures", () => {
  const missing = validateGameConfig(fixture("invalid-missing-entities.json"));
  assert.equal(missing.valid, false);
  assert.ok(missing.errors.some(({ code, path }) => code === "REQUIRED" && path === "$.entities"));
  const remote = validateGameConfig(fixture("invalid-remote-resource.json"));
  assert.ok(remote.errors.some(({ code, path }) => code === "RESOURCE_KEY_NOT_ALLOWED" && path === "$.player.spriteKey"));
  const bounds = validateGameConfig(fixture("invalid-out-of-bounds-patrol.json"));
  assert.ok(bounds.errors.some(({ code, path }) => code === "WORLD_BOUNDS" && path === "$.behaviors.enemyPatrols[0].distance"));
});

test("migrates the historical fixture deterministically without keeping aliases", () => {
  const legacy = fixture("legacy-valid-1.0.json");
  const first = validateGameConfig(legacy);
  const second = validateGameConfig(JSON.stringify({ game_config: legacy }));
  assert.equal(first.valid, true);
  assert.equal(first.migrated, true);
  assert.deepEqual(first.config, second.config);
  assert.equal(first.config.metadata.schemaVersion, "2.0");
  assert.equal(first.config.metadata.gameType, "arcade_collect");
  assert.equal(first.config.entities.enemies[0].id, "enemy-1");
  assert.equal(first.config.behaviors.enemyPatrols[0].distance, 120);
  assert.equal("theme" in first.config, false);
  assert.equal("rules" in first.config, false);
  assert.deepEqual(first.config, fixture("legacy-valid-1.0.migrated.json"));
  assert.deepEqual(migrateLegacyGameConfig(legacy), first.config);
});

test("does not normalize missing structures, aliases, numeric strings, or unknown templates", () => {
  const incomplete = { metadata: { schemaVersion: "2.0", gameType: "arcade_collect", title: "Incomplete", seed: 1 } };
  assert.equal(validateGameConfig(incomplete).valid, false);
  assert.throws(() => normalizeGameConfig(incomplete), /REQUIRED/);
  const alias = structuredClone(defaultGameConfig);
  alias.items = alias.entities.collectibles;
  assert.ok(validateGameConfig(alias).errors.some(({ code }) => code === "UNKNOWN_FIELD"));
  const numericString = structuredClone(defaultGameConfig);
  numericString.viewport.width = "960";
  assert.equal(validateGameConfig(numericString).valid, false);
  const unsupported = structuredClone(defaultGameConfig);
  unsupported.metadata.gameType = "platformer";
  assert.equal(validateGameConfig(unsupported).valid, false);
});

test("accepts closed interval gameplay boundary values", () => {
  const config = structuredClone(defaultGameConfig);
  config.player.speed = 80;
  config.player.maxHealth = 5;
  config.balance.timeLimitSeconds = 600;
  config.balance.winBonus = 0;
  assert.equal(validateGameConfig(config).valid, true);
});

test("rejects required entities that overlap obstacles", () => {
  const config = structuredClone(defaultGameConfig);
  config.world.spawn = { x: 360, y: 180 };
  assert.ok(validateGameConfig(config).errors.some(({ code, path }) => code === "WORLD_OVERLAP" && path === "$.world.spawn"));
});
