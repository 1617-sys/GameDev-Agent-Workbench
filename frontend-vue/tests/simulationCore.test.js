import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { createSimulation, sha256Hex, SimulationCore, TICK_MS } from "../src/features/demo/runtime/simulation/index.ts";
import { validateGameConfig } from "../src/features/demo/runtime/gameConfig.js";

const fixturePath = fileURLToPath(new URL("../../docs/requirements/v3/examples/game-config-2.0/valid-minimal.json", import.meta.url));
const simulationDirectory = fileURLToPath(new URL("../src/features/demo/runtime/simulation", import.meta.url));

function configFixture() {
  const config = JSON.parse(readFileSync(fixturePath, "utf8"));
  config.viewport = { width: 640, height: 360, scaleMode: "fit" };
  config.world = { width: 640, height: 360, spawn: { x: 40, y: 180 }, obstacles: [] };
  config.player = { ...config.player, speed: 100, size: 28, maxHealth: 3, hitInvulnerabilityMs: 100 };
  config.entities.collectibles = [{
    id: "item-1", x: 300, y: 180, size: 18, score: 100, label: "测试目标", spriteKey: "collectible.gem"
  }];
  config.entities.enemies = [];
  config.entities.exit = { x: 600, y: 180, width: 54, height: 72, label: "出口", spriteKey: "exit.door" };
  config.behaviors = { enemyPatrols: [], contact: { damage: 1 } };
  config.objectives = { targetCollectibles: 1, winCondition: "collect_target_then_exit", loseConditions: ["health_depleted", "time_expired"] };
  config.balance = { timeLimitSeconds: 30, winBonus: 500, difficulty: "normal" };
  assert.equal(validateGameConfig(config).valid, true);
  return config;
}

function options(overrides = {}) {
  return {
    protocolVersion: "simulation/1.0",
    episodeId: "episode-simulation-core-test",
    configDigest: "a".repeat(64),
    seed: 20260715,
    maxSteps: 1000,
    observationPolicy: { kind: "FULL" },
    ...overrides
  };
}

function sourceFiles(directory) {
  return readdirSync(directory).flatMap((name) => {
    const path = `${directory}/${name}`;
    return statSync(path).isDirectory() ? sourceFiles(path) : [path];
  });
}

test("keeps the simulation module independent from UI, engine, DOM and transport dependencies", () => {
  const source = sourceFiles(simulationDirectory).map((path) => readFileSync(path, "utf8")).join("\n");
  assert.doesNotMatch(source, /\b(?:Phaser|window|document|Vue|fetch|XMLHttpRequest|WebSocket|Date|performance|setTimeout|setInterval)\b/);
  assert.equal(TICK_MS, 50);
  assert.equal(sha256Hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
});

test("replays identical actions deterministically and returns immutable copies", () => {
  const config = configFixture();
  const first = createSimulation(config, options());
  const second = createSimulation(config, options());
  const initial = first.snapshot();
  assert.equal(Object.isFrozen(initial), true);
  assert.equal(Object.isFrozen(initial.player.position), true);
  assert.throws(() => { initial.player.position.xMp = 999_000; }, TypeError);

  config.world.spawn.x = 500;
  const actions = ["MOVE_RIGHT", "MOVE_RIGHT", "WAIT", "MOVE_DOWN", "RESTART", "MOVE_LEFT"];
  const firstResults = actions.map((type) => first.step({ type }));
  const secondResults = actions.map((type) => second.step({ type }));
  assert.deepEqual(firstResults, secondResults);
  assert.equal(first.snapshot().player.position.xMp, 35_000);
  assert.equal(first.snapshot().attempt, 2);
  assert.equal(first.snapshot().restartCount, 1);
  assert.equal(firstResults.every((result) => Object.isFrozen(result) && Object.isFrozen(result.observation)), true);
});

test("uses continuous axis sweeps for obstacle and world boundaries", () => {
  const config = configFixture();
  config.world.spawn = { x: 15, y: 180 };
  config.world.obstacles = [{ id: "wall-1", x: 50, y: 180, width: 24, height: 80, spriteKey: "obstacle.stone" }];
  assert.equal(validateGameConfig(config).valid, true);
  const simulation = createSimulation(config, options());

  simulation.step({ type: "MOVE_RIGHT" });
  simulation.step({ type: "MOVE_RIGHT" });
  simulation.step({ type: "MOVE_RIGHT" });
  assert.equal(simulation.snapshot().player.position.xMp, 24_000);
  simulation.step({ type: "MOVE_RIGHT" });
  assert.equal(simulation.snapshot().player.position.xMp, 24_000);

  simulation.step({ type: "MOVE_LEFT" });
  simulation.step({ type: "MOVE_LEFT" });
  simulation.step({ type: "MOVE_LEFT" });
  assert.equal(simulation.snapshot().player.position.xMp, 14_000);
  simulation.step({ type: "MOVE_LEFT" });
  assert.equal(simulation.snapshot().player.position.xMp, 14_000);
});

test("moves seeded enemies to patrol limits and reverses once for the next tick", () => {
  const config = configFixture();
  config.entities.enemies = [{ id: "enemy-1", x: 300, y: 80, size: 28, speed: 240, spriteKey: "enemy.guard" }];
  config.behaviors.enemyPatrols = [{ enemyId: "enemy-1", axis: "x", distance: 32 }];
  assert.equal(validateGameConfig(config).valid, true);
  const simulation = createSimulation(config, options());
  const initialDirection = simulation.snapshot().enemies[0].direction;

  simulation.step({ type: "WAIT" });
  simulation.step({ type: "WAIT" });
  simulation.step({ type: "WAIT" });
  const atLimit = simulation.snapshot().enemies[0];
  assert.equal(Math.abs(atLimit.position.xMp - atLimit.originMp), 32_000);
  assert.equal(atLimit.direction, initialDirection * -1);
  simulation.step({ type: "WAIT" });
  assert.equal(Math.abs(simulation.snapshot().enemies[0].position.xMp - atLimit.originMp), 20_000);
});

test("collects before checking the unlocked exit and terminates with WON", () => {
  const config = configFixture();
  config.entities.collectibles[0].x = 40;
  config.entities.exit.x = 40;
  assert.equal(validateGameConfig(config).valid, true);
  const result = createSimulation(config, options()).step({ type: "WAIT" });

  assert.equal(result.terminationReason, "WON");
  assert.equal(result.scoreDelta, 600);
  assert.deepEqual(result.events.map((event) => event.type), ["ITEM_COLLECTED", "GAME_WON"]);
  assert.equal(result.observation.progress.score, 600);
  assert.equal(result.observation.progress.exitUnlocked, true);
});

test("applies at most one contact hit per tick and honors the invulnerability window", () => {
  const config = configFixture();
  config.player.maxHealth = 2;
  config.world.spawn.x = 60;
  config.entities.enemies = [
    { id: "enemy-b", x: 60, y: 180, size: 28, speed: 20, spriteKey: "enemy.guard" },
    { id: "enemy-a", x: 60, y: 180, size: 28, speed: 20, spriteKey: "enemy.drone" }
  ];
  config.behaviors.enemyPatrols = [
    { enemyId: "enemy-b", axis: "x", distance: 32 },
    { enemyId: "enemy-a", axis: "x", distance: 32 }
  ];
  assert.equal(validateGameConfig(config).valid, true);
  const simulation = createSimulation(config, options());

  const first = simulation.step({ type: "WAIT" });
  assert.equal(simulation.snapshot().player.health, 1);
  assert.deepEqual(first.events, [{ type: "PLAYER_HIT", payload: { enemyId: "enemy-a" } }]);
  const second = simulation.step({ type: "WAIT" });
  assert.equal(second.events.length, 0);
  const third = simulation.step({ type: "WAIT" });
  assert.equal(third.terminationReason, "HEALTH_DEPLETED");
  assert.deepEqual(third.events.map((event) => event.type), ["PLAYER_HIT", "GAME_LOST"]);
});

test("gives TIME_EXPIRED priority over movement, collection and winning on the last tick", () => {
  const config = configFixture();
  config.entities.collectibles[0].x = 65;
  config.entities.exit.x = 40;
  assert.equal(validateGameConfig(config).valid, true);
  const simulation = createSimulation(config, options());
  for (let index = 0; index < 599; index += 1) simulation.step({ type: "WAIT" });
  const result = simulation.step({ type: "MOVE_RIGHT" });

  assert.equal(result.terminationReason, "TIME_EXPIRED");
  assert.deepEqual(result.events, [{ type: "GAME_LOST", payload: { reason: "TIME_EXPIRED" } }]);
  assert.equal(simulation.snapshot().player.position.xMp, 40_000);
  assert.equal(simulation.snapshot().collectibles[0].active, true);
});

test("terminates at MAX_STEPS after completing the final allowed world step", () => {
  const simulation = createSimulation(configFixture(), options({ maxSteps: 1 }));
  const result = simulation.step({ type: "MOVE_RIGHT" });
  assert.equal(result.terminationReason, "MAX_STEPS");
  assert.equal(result.step, 1);
  assert.equal(result.events.length, 0);
  assert.equal(simulation.snapshot().player.position.xMp, 45_000);
});

test("rejects illegal actions without advancing time, step or state hash", () => {
  const simulation = createSimulation(configFixture(), options());
  const before = simulation.snapshot();
  const beforeHash = simulation.stateHash();
  const result = simulation.step({ type: "MOVE_NORTH_EAST", durationMs: 250 });
  assert.equal(result.accepted, false);
  assert.equal(result.error.code, "INVALID_ACTION");
  assert.equal(result.previousStateHash, beforeHash);
  assert.equal(result.stateHash, beforeHash);
  assert.deepEqual(simulation.snapshot(), before);
});

test("turns an internal invariant failure into one terminal ERROR result", () => {
  class FaultySimulation extends SimulationCore {
    beforeFinalize() {
      throw new Error("injected test failure");
    }
  }
  const simulation = new FaultySimulation(configFixture(), options());
  const result = simulation.step({ type: "WAIT" });
  assert.equal(result.accepted, true);
  assert.equal(result.advanced, false);
  assert.equal(result.terminationReason, "ERROR");
  assert.equal(result.error.code, "INTERNAL_ERROR");
  const rejected = simulation.step({ type: "WAIT" });
  assert.equal(rejected.accepted, false);
  assert.equal(rejected.error.code, "EPISODE_TERMINATED");
});
