import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { ArcadeCollectStateMachine, deterministicEnemyDirections, RUNTIME_STATES } from "../src/features/demo/runtime/runtimeState.js";

const fixturePath = fileURLToPath(new URL("../../docs/requirements/v3/examples/game-config-2.0/valid-minimal.json", import.meta.url));
const fixture = () => JSON.parse(readFileSync(fixturePath, "utf8"));

test("uses the explicit READY, PLAYING, PAUSED, WON and LOST state vocabulary", () => {
  assert.deepEqual(Object.values(RUNTIME_STATES), ["READY", "PLAYING", "PAUSED", "WON", "LOST"]);
  const machine = new ArcadeCollectStateMachine(fixture());
  assert.equal(machine.state.status, "READY");
  assert.equal(machine.pause(), false);
  assert.equal(machine.start(), true);
  assert.equal(machine.pause(), true);
  assert.equal(machine.state.status, "PAUSED");
  assert.equal(machine.resume(), true);
  assert.equal(machine.state.status, "PLAYING");
});
test("scores each item once, unlocks the exit and applies the win bonus", () => {
  const machine = new ArcadeCollectStateMachine(fixture());
  machine.start();
  assert.equal(machine.collect("item-1"), true);
  assert.equal(machine.collect("item-1"), false);
  assert.equal(machine.collect("item-2"), true);
  assert.equal(machine.state.exitUnlocked, true);
  assert.equal(machine.state.score, 200);
  assert.equal(machine.reachExit(), true);
  assert.equal(machine.state.status, "WON");
  assert.equal(machine.state.score, 700);
  assert.equal(machine.collect("item-1"), false);
  assert.equal(machine.hit(), false);
});

test("applies damage only after invulnerability and loses when health is depleted", () => {
  const machine = new ArcadeCollectStateMachine(fixture());
  machine.start();
  assert.equal(machine.hit(), true);
  assert.equal(machine.state.health, 2);
  assert.equal(machine.hit(), false);
  machine.tick(1000);
  assert.equal(machine.hit(), true);
  machine.tick(1000);
  assert.equal(machine.hit(), true);
  assert.equal(machine.state.status, "LOST");
  assert.equal(machine.state.outcomeReason, "HEALTH_DEPLETED");
});

test("countdown advances only while playing and produces TIME_EXPIRED", () => {
  const config = fixture();
  config.balance.timeLimitSeconds = 30;
  const machine = new ArcadeCollectStateMachine(config);
  machine.tick(10_000);
  assert.equal(machine.state.remainingMs, 30_000);
  machine.start();
  machine.tick(10_000);
  machine.pause();
  machine.tick(10_000);
  assert.equal(machine.state.remainingMs, 20_000);
  machine.resume();
  machine.tick(20_000);
  assert.equal(machine.state.status, "LOST");
  assert.equal(machine.state.outcomeReason, "TIME_EXPIRED");
});

test("the same config seed reproduces initial enemy directions after restart", () => {
  const config = fixture();
  const expected = deterministicEnemyDirections(config);
  const first = new ArcadeCollectStateMachine(config);
  first.start();
  first.reset();
  const second = new ArcadeCollectStateMachine(config);
  assert.deepEqual(first.state.enemyDirections, expected);
  assert.deepEqual(second.state.enemyDirections, expected);
  assert.equal(first.state.restartCount, 1);
});
