import test from "node:test";
import assert from "node:assert/strict";
import { buildPlayerRunRequest, canStartPlayerRun, createSingleFlightSubmitter } from "../src/features/prototypes/playerRunForm.js";

const form = { personaId: "NOVICE", policyKind: "LLM", maxSteps: 300, concurrency: 2, seed: 42 };

test("builds bounded Player Run episodes from the selected version", () => {
  const result = buildPlayerRunRequest(form, "version-1", "batch-key");
  assert.equal(result.prototypeVersionUuid, "version-1");
  assert.equal(result.episodes[0].personaId, "NOVICE");
  assert.equal(result.episodes[0].maxSteps, 300);
  assert.equal(result.episodes[0].policySeed, 42);
});

test("permission, budget confirmation and busy state all gate submission", () => {
  assert.equal(canStartPlayerRun({ capabilities: [], confirmed: true, busy: false }), false);
  assert.equal(canStartPlayerRun({ capabilities: ["player-runs.create"], confirmed: false, busy: false }), false);
  assert.equal(canStartPlayerRun({ capabilities: ["player-runs.create"], confirmed: true, busy: true }), false);
  assert.equal(canStartPlayerRun({ capabilities: ["player-runs.create"], confirmed: true, busy: false }), true);
});

test("double click shares one request and backend failure is preserved", async () => {
  let calls = 0;
  const submit = createSingleFlightSubmitter(async () => { calls += 1; await new Promise(resolve => setTimeout(resolve, 5)); return { runUuid: "run-1" }; });
  const [left, right] = await Promise.all([submit(), submit()]);
  assert.equal(calls, 1);
  assert.equal(left.runUuid, right.runUuid);

  const failing = createSingleFlightSubmitter(async () => { throw new Error("预算超过后端限制"); });
  await assert.rejects(failing(), /预算超过后端限制/);
});
