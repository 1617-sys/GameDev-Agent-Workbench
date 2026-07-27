import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import {
  RANDOM_ALGORITHM_VERSION,
  SeededRandom,
  canonicalStringify,
  createSimulation,
  recordActionSequence,
  replayActionSequence,
  sha256Hex
} from "../src/features/demo/runtime/simulation/index.ts";
import { validateGameConfig } from "../src/features/demo/runtime/gameConfig.js";

const fixturePath = fileURLToPath(new URL("../../docs/requirements/v3/examples/game-config-2.0/valid-minimal.json", import.meta.url));

function fixture() {
  return JSON.parse(readFileSync(fixturePath, "utf8"));
}

function options(overrides = {}) {
  return {
    protocolVersion: "simulation/1.0",
    episodeId: "episode-replay-test",
    configDigest: "b".repeat(64),
    seed: 20260715,
    maxSteps: 1000,
    observationPolicy: { kind: "FULL" },
    ...overrides
  };
}

function winningFixture() {
  const config = fixture();
  const spawn = config.world.spawn;
  config.entities.collectibles = [{
    id: "item-1",
    x: spawn.x,
    y: spawn.y,
    size: 18,
    score: 100,
    label: "Replay target",
    spriteKey: "collectible.gem"
  }];
  config.entities.enemies = [];
  config.behaviors.enemyPatrols = [];
  config.entities.exit = {
    x: spawn.x,
    y: spawn.y,
    width: 54,
    height: 72,
    label: "Replay exit",
    spriteKey: "exit.door"
  };
  config.objectives.targetCollectibles = 1;
  assert.equal(validateGameConfig(config).valid, true);
  return config;
}

test("produces the same terminal hash for the same inputs across 100 runs", () => {
  const config = fixture();
  const actions = Array.from({ length: 60 }, (_, index) => ({
    type: ["MOVE_RIGHT", "MOVE_DOWN", "WAIT", "MOVE_LEFT", "MOVE_UP"][index % 5]
  }));
  const hashes = new Set();
  for (let run = 0; run < 100; run += 1) {
    const trace = recordActionSequence(config, options({ maxSteps: actions.length }), actions);
    assert.equal(trace.terminationReason, "MAX_STEPS");
    hashes.add(trace.finalStateHash);
  }
  assert.equal(hashes.size, 1);
});

test("records an explicit PRNG version and different seeds produce different streams and hashes", () => {
  const firstRandom = new SeededRandom(1);
  const secondRandom = new SeededRandom(2);
  assert.equal(firstRandom.algorithm, RANDOM_ALGORITHM_VERSION);
  assert.notEqual(firstRandom.next(), secondRandom.next());

  const actions = [{ type: "WAIT" }, { type: "WAIT" }];
  const first = recordActionSequence(fixture(), options({ seed: 1 }), actions);
  const second = recordActionSequence(fixture(), options({ seed: 2 }), actions);
  assert.equal(first.randomAlgorithm, "mulberry32/1.0");
  assert.notEqual(first.finalStateHash, second.finalStateHash);

  const noEnemies = fixture();
  noEnemies.entities.enemies = [];
  noEnemies.behaviors.enemyPatrols = [];
  const noRandomBehavior = recordActionSequence(noEnemies, options({ seed: 7 }), [{ type: "WAIT" }]);
  assert.equal(noRandomBehavior.seed, 7);
  assert.equal(noRandomBehavior.randomAlgorithm, "mulberry32/1.0");
});

test("canonical snapshot hashes every serialized state field deterministically", () => {
  const simulation = createSimulation(fixture(), options());
  simulation.step({ type: "MOVE_RIGHT" });
  const canonical = simulation.canonicalSnapshot();
  assert.equal(canonical, canonicalStringify(simulation.snapshot()));
  assert.equal(simulation.stateHash(), sha256Hex(canonical));
  assert.match(canonical, /"collectedIds"/);
  assert.match(canonical, /"enemies"/);
  assert.match(canonical, /"player"/);
  assert.match(canonical, /"remainingMs"/);
});

test("replays every step including the explicit rejection after termination", () => {
  const config = winningFixture();
  const replayOptions = options({ maxSteps: 10 });
  const trace = recordActionSequence(config, replayOptions, [{ type: "WAIT" }, { type: "WAIT" }]);
  assert.equal(Object.isFrozen(trace), true);
  assert.equal(Object.isFrozen(trace.steps[0]), true);
  assert.equal(trace.steps[0].expectedTerminationReason, "WON");
  assert.equal(trace.steps[1].expectedAccepted, false);
  assert.equal(trace.steps[1].expectedTerminationReason, "WON");
  assert.equal(trace.steps[0].expectedStateHash, trace.steps[1].expectedStateHash);

  const replay = replayActionSequence(config, replayOptions, trace);
  assert.equal(replay.ok, true);
  assert.equal(replay.comparedSteps, 2);
  assert.equal(replay.finalStateHash, trace.finalStateHash);
  assert.equal(replay.terminationReason, "WON");
});

test("rejects protocol and config digest mismatches before executing replay", () => {
  const config = fixture();
  const trace = recordActionSequence(config, options(), [{ type: "WAIT" }]);
  const protocolTrace = structuredClone(trace);
  protocolTrace.protocolVersion = "simulation/2.0";
  const protocol = replayActionSequence(config, options(), protocolTrace);
  assert.equal(protocol.ok, false);
  assert.equal(protocol.error.code, "REPLAY_PROTOCOL_MISMATCH");
  assert.equal(protocol.error.sequence, 0);

  const digest = replayActionSequence(config, options({ configDigest: "c".repeat(64) }), trace);
  assert.equal(digest.ok, false);
  assert.equal(digest.error.code, "REPLAY_CONFIG_DIGEST_MISMATCH");
  assert.equal(digest.error.sequence, 0);
});

test("detects a tampered action before stepping and reports its first sequence", () => {
  const config = fixture();
  const trace = structuredClone(recordActionSequence(config, options(), [
    { type: "MOVE_RIGHT" },
    { type: "MOVE_DOWN" },
    { type: "WAIT" }
  ]));
  trace.steps[1].requestedAction = { type: "MOVE_LEFT" };
  const replay = replayActionSequence(config, options(), trace);
  assert.equal(replay.ok, false);
  assert.equal(replay.error.code, "REPLAY_ACTION_DIGEST_MISMATCH");
  assert.equal(replay.error.sequence, 2);
  assert.equal(replay.comparedSteps, 1);
});

test("points to the first state hash divergence without hiding earlier matching steps", () => {
  const config = fixture();
  const trace = structuredClone(recordActionSequence(config, options(), [
    { type: "MOVE_RIGHT" },
    { type: "MOVE_DOWN" },
    { type: "WAIT" }
  ]));
  trace.steps[2].expectedStateHash = "0".repeat(64);
  const replay = replayActionSequence(config, options(), trace);
  assert.equal(replay.ok, false);
  assert.equal(replay.error.code, "REPLAY_STATE_HASH_MISMATCH");
  assert.equal(replay.error.sequence, 3);
  assert.equal(replay.comparedSteps, 3);
  assert.notEqual(replay.error.expected, replay.error.actual);
});
