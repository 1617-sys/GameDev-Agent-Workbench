import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { defaultGameConfig } from "../src/features/demo/runtime/gameConfig.js";
import {
  RuntimeSimulationAdapter,
  replayActionSequence
} from "../src/features/demo/runtime/simulation/index.ts";

function fixture() {
  return JSON.parse(JSON.stringify(defaultGameConfig));
}

test("keeps gameplay rules out of the Phaser projection layer", () => {
  const runtimePath = fileURLToPath(new URL("../src/features/demo/runtime/topDownCollectRuntime.js", import.meta.url));
  const source = readFileSync(runtimePath, "utf8");
  assert.doesNotMatch(source, /ArcadeCollectStateMachine|RUNTIME_STATES/);
  assert.doesNotMatch(source, /physics\.add\.(?:collider|overlap)/);
  assert.doesNotMatch(source, /collectItem\(|hitPlayer\(|tryWin\(|finishRuntime\(/);
  assert.match(source, /adapter\.advance\(delta, this\.inputAction\(\)\)/);
  assert.match(source, /for \(const event of result\.events\)/);
});

test("advances the Simulation Core only on fixed 50 ms ticks", () => {
  const adapter = new RuntimeSimulationAdapter(fixture());
  assert.equal(adapter.status(), "READY");
  assert.equal(adapter.start(), true);
  assert.deepEqual(adapter.advance(49, { type: "MOVE_RIGHT" }), []);
  assert.equal(adapter.snapshot().step, 0);

  const results = adapter.advance(1, { type: "MOVE_RIGHT" });
  assert.equal(results.length, 1);
  assert.equal(results[0].appliedAction.type, "MOVE_RIGHT");
  assert.equal(adapter.snapshot().step, 1);
  assert.equal(adapter.snapshot().elapsedMs, 50);
});

test("records the exact browser action sequence as a replayable Core trace", () => {
  const config = fixture();
  const adapter = new RuntimeSimulationAdapter(config);
  adapter.start();
  adapter.advance(150, { type: "MOVE_RIGHT" });
  adapter.advance(100, { type: "MOVE_DOWN" });
  adapter.advance(50, { type: "WAIT" });

  const trace = adapter.replayTrace();
  const replay = replayActionSequence(config, adapter.simulationOptions(), trace);
  assert.equal(trace.steps.length, 6);
  assert.equal(trace.finalStateHash, adapter.stateHash());
  assert.equal(replay.ok, true);
  assert.equal(replay.finalStateHash, adapter.stateHash());
});

test("surfaces each StepResult telemetry event once and restarts through the protocol", () => {
  const config = fixture();
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
  config.objectives.targetCollectibles = 1;
  config.entities.exit.x = config.world.spawn.x + 200;
  config.entities.exit.y = config.world.spawn.y;

  const adapter = new RuntimeSimulationAdapter(config);
  adapter.start();
  const [collected] = adapter.advance(50, { type: "WAIT" });
  assert.deepEqual(collected.events.map((event) => event.type), ["ITEM_COLLECTED"]);

  const restart = adapter.restart();
  assert.equal(restart.recreated, false);
  assert.deepEqual(restart.result.events.map((event) => event.type), ["SESSION_RESTARTED"]);
  assert.equal(adapter.hudState().collected, 0);
  assert.equal(adapter.hudState().status, "PLAYING");
});

test("keeps pause in the host without advancing Core state", () => {
  const adapter = new RuntimeSimulationAdapter(fixture());
  adapter.start();
  assert.equal(adapter.togglePause(), true);
  assert.equal(adapter.status(), "PAUSED");
  assert.deepEqual(adapter.advance(500, { type: "MOVE_LEFT" }), []);
  assert.equal(adapter.snapshot().step, 0);
  assert.equal(adapter.togglePause(), true);
  assert.equal(adapter.status(), "PLAYING");
});
