import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { defaultGameConfig, validateGameConfig } from "../src/features/demo/runtime/gameConfig.js";
import {
  EPISODE_PROTOCOL_VERSION,
  HEADLESS_LIMITS,
  HeadlessRunnerValidationError,
  runHeadlessBatch,
  runHeadlessEpisode
} from "../src/features/demo/runtime/headless/index.ts";
import { hashCanonical } from "../src/features/demo/runtime/simulation/index.ts";

const BATCH_ID = "00000000-0000-4000-8000-000000000001";

function fakeClock(step = 1) {
  let value = 0;
  return {
    now() { value += step; return value; },
    async yieldControl() {}
  };
}

function configFixture({ winning = false } = {}) {
  const config = JSON.parse(JSON.stringify(defaultGameConfig));
  if (winning) {
    config.world.obstacles = [];
    config.entities.collectibles = [{
      id: "item-1",
      x: config.world.spawn.x,
      y: config.world.spawn.y,
      size: 18,
      score: 100,
      label: "Target",
      spriteKey: "collectible.gem"
    }];
    config.entities.enemies = [];
    config.behaviors.enemyPatrols = [];
    config.entities.exit = {
      x: config.world.spawn.x,
      y: config.world.spawn.y,
      width: 54,
      height: 72,
      label: "Exit",
      spriteKey: "exit.door"
    };
    config.objectives.targetCollectibles = 1;
  }
  const validated = validateGameConfig(config);
  assert.equal(validated.valid, true);
  return validated.config;
}

function episodeId(index = 1) {
  return `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
}

function request(config, index = 1, overrides = {}) {
  const base = {
    episodeProtocolVersion: EPISODE_PROTOCOL_VERSION,
    clientEpisodeKey: `baseline-${index}`,
    prototype: {
      projectUuid: "project-demo-001",
      prototypeVersionUuid: "version-demo-003",
      gameConfigArtifactUuid: "artifact-config-003",
      configDigest: hashCanonical(config),
      gameConfigSchemaVersion: "game-config/2.0",
      runtimeCapabilityVersion: "arcade-collect-runtime/1"
    },
    simulation: {
      protocolVersion: "simulation/1.0",
      coreVersion: "simulation-core/1.0.0+test",
      seed: config.metadata.seed,
      maxSteps: 100,
      observationPolicy: { kind: "FULL" }
    },
    policy: {
      kind: "DETERMINISTIC",
      policyId: "fixture-actions",
      policyVersion: "1.0.0",
      policyDigest: "a".repeat(64)
    },
    persona: {
      personaId: "baseline-neutral",
      personaVersion: "1.0.0",
      personaDigest: "b".repeat(64)
    },
    model: null,
    metricVersion: "score-delta/1.0",
    experiment: null,
    labels: {}
  };
  return { ...base, ...overrides };
}

function invocation(config, index = 1, overrides = {}) {
  return {
    episodeId: episodeId(index),
    batchId: BATCH_ID,
    request: request(config, index),
    gameConfig: config,
    actionSequence: [{ type: "WAIT" }],
    actionMode: "CYCLE",
    timeoutMs: 10_000,
    ...overrides
  };
}

test("keeps the Node runner independent from Phaser, Vue, DOM and backend transports", () => {
  const sourcePath = fileURLToPath(new URL("../src/features/demo/runtime/headless/index.ts", import.meta.url));
  const source = readFileSync(sourcePath, "utf8");
  assert.doesNotMatch(source, /\b(?:Phaser|Vue|window|document|fetch|XMLHttpRequest|WebSocket)\b/);
  assert.doesNotMatch(source, /topDownCollectRuntime|runtimeState/);
});

test("runs a JSON-only deterministic episode and returns the Episode RFC result and trajectory", async () => {
  const config = configFixture({ winning: true });
  const result = await runHeadlessEpisode(invocation(config), fakeClock());

  assert.equal(result.executionStatus, "COMPLETED");
  assert.equal(result.terminationReason, "WON");
  assert.equal(result.outcome, "WON");
  assert.equal(result.sampleSource, "MACHINE");
  assert.equal(result.stepCount, 1);
  assert.equal(result.finalScore, 600);
  assert.deepEqual(result.steps[0].transition.events.map((event) => event.type), ["ITEM_COLLECTED", "GAME_WON"]);
  assert.match(result.trajectoryDigest, /^[0-9a-f]{64}$/);
  assert.deepEqual(JSON.parse(JSON.stringify(result)), result);
});

test("repeating the same request produces identical state, steps and trajectory digest", async () => {
  const config = configFixture({ winning: true });
  const input = invocation(config);
  const first = await runHeadlessEpisode(input, fakeClock());
  const second = await runHeadlessEpisode(input, fakeClock());
  assert.deepEqual(second, first);
});

test("records an illegal action without advancing Core and continues to a valid terminal action", async () => {
  const config = configFixture({ winning: true });
  const input = invocation(config, 1, {
    actionSequence: [{ type: "MOVE_DIAGONAL", durationMs: 250 }, { type: "WAIT" }]
  });
  const result = await runHeadlessEpisode(input, fakeClock());

  assert.equal(result.executionStatus, "COMPLETED");
  assert.equal(result.invalidActionCount, 1);
  assert.equal(result.acceptedActionCount, 1);
  assert.equal(result.steps[0].transition.accepted, false);
  assert.equal(result.steps[0].simulationStepBefore, result.steps[0].simulationStepAfter);
  assert.equal(result.steps[0].reward.valueMicros, 0);
  assert.equal(result.terminationReason, "WON");
});

test("rejects an invalid GameConfig before creating Core state", async () => {
  const config = configFixture();
  const invalid = JSON.parse(JSON.stringify(config));
  invalid.world.width = -1;
  const result = await runHeadlessEpisode(invocation(config, 1, { gameConfig: invalid }), fakeClock());
  assert.equal(result.executionStatus, "REJECTED");
  assert.equal(result.error.code, "INVALID_GAME_CONFIG");
  assert.equal(result.finalStateHash, null);
  assert.equal(result.trajectoryDigest, null);
});

test("fails safely when the wall-clock timeout expires", async () => {
  const config = configFixture();
  const result = await runHeadlessEpisode(invocation(config, 1, { timeoutMs: 5 }), fakeClock(10));
  assert.equal(result.executionStatus, "FAILED");
  assert.equal(result.error.code, "EPISODE_TIMEOUT");
  assert.equal(result.stepCount, 0);
  assert.match(result.finalStateHash, /^[0-9a-f]{64}$/);
});

test("maps the Core maximum-step boundary to a completed truncated Episode", async () => {
  const config = configFixture();
  const input = invocation(config);
  input.request.simulation.maxSteps = 2;
  input.maxDecisions = 2;
  const result = await runHeadlessEpisode(input, fakeClock());
  assert.equal(result.executionStatus, "COMPLETED");
  assert.equal(result.terminationReason, "MAX_STEPS");
  assert.equal(result.outcome, "TRUNCATED");
  assert.equal(result.acceptedActionCount, 2);
});

test("runs 100 bounded episodes and reports execution and termination statistics", async () => {
  const config = configFixture({ winning: true });
  const episodes = Array.from({ length: 100 }, (_, index) => invocation(config, index + 1));
  const batch = await runHeadlessBatch({
    episodeProtocolVersion: EPISODE_PROTOCOL_VERSION,
    batchId: BATCH_ID,
    clientBatchKey: "one-hundred-winning-episodes",
    concurrency: HEADLESS_LIMITS.maxConcurrency,
    timeoutMs: 60_000,
    episodes
  }, fakeClock());

  assert.equal(batch.status, "SUCCEEDED");
  assert.deepEqual(batch.counts, { total: 100, queued: 0, running: 0, completed: 100, failed: 0, rejected: 0, cancelled: 0 });
  assert.deepEqual(batch.statistics.executionStatuses, { completed: 100, failed: 0, rejected: 0, cancelled: 0 });
  assert.deepEqual(batch.statistics.terminationReasons, { WON: 100 });
  assert.equal(batch.results.length, 100);
});

test("preserves completed sibling results when another batch item is rejected", async () => {
  const config = configFixture({ winning: true });
  const invalid = invocation(config, 2);
  invalid.gameConfig = { broken: true };
  const batch = await runHeadlessBatch({
    episodeProtocolVersion: EPISODE_PROTOCOL_VERSION,
    batchId: BATCH_ID,
    clientBatchKey: "partial-batch",
    concurrency: 2,
    timeoutMs: 10_000,
    episodes: [invocation(config, 1), invalid]
  }, fakeClock());

  assert.equal(batch.status, "PARTIAL_SUCCESS");
  assert.equal(batch.counts.completed, 1);
  assert.equal(batch.counts.rejected, 1);
  assert.equal(batch.results[0].executionStatus, "COMPLETED");
  assert.equal(batch.results[1].executionStatus, "REJECTED");
});

test("rejects unbounded or malformed batch controls before starting any episode", async () => {
  const config = configFixture();
  await assert.rejects(() => runHeadlessBatch({
    episodeProtocolVersion: EPISODE_PROTOCOL_VERSION,
    batchId: BATCH_ID,
    clientBatchKey: "invalid-concurrency",
    concurrency: HEADLESS_LIMITS.maxConcurrency + 1,
    timeoutMs: 10_000,
    episodes: [invocation(config)]
  }), (cause) => cause instanceof HeadlessRunnerValidationError && cause.code === "INVALID_CONCURRENCY");
});
