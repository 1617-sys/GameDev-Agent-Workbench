import test from "node:test";
import assert from "node:assert/strict";
import { playerRunsApi, validatePlayerRunRequest } from "../src/shared/api/playerRuns.js";

const request = () => ({
  prototypeVersionUuid: "version-1",
  clientBatchKey: "batch-1",
  concurrency: 2,
  episodes: [{
    clientEpisodeKey: "episode-1", personaId: "NOVICE", policyKind: "DETERMINISTIC",
    seed: 7, maxSteps: 500, policySeed: 9, modelKey: "default"
  }]
});

test("validates version, persona and execution budget", () => {
  assert.deepEqual(validatePlayerRunRequest(request()), request());
  assert.throws(() => validatePlayerRunRequest({ ...request(), prototypeVersionUuid: "" }), /版本/);
  assert.throws(() => validatePlayerRunRequest({ ...request(), episodes: [{ ...request().episodes[0], personaId: "ROOT" }] }), /Persona/);
  assert.throws(() => validatePlayerRunRequest({ ...request(), episodes: [{ ...request().episodes[0], maxSteps: 10001 }] }), /预算/);
});

test("creates a project-scoped Player Run with idempotency and trace headers", async () => {
  const originalWindow = globalThis.window;
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.window = { setTimeout, clearTimeout, sessionStorage: { getItem: () => "token" } };
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    return { ok: true, status: 200, json: async () => ({ code: 0, data: { runUuid: "player-1" } }) };
  };
  try { await playerRunsApi.create("project one", request(), "player-key", "trace-1"); }
  finally { globalThis.window = originalWindow; globalThis.fetch = originalFetch; }

  assert.match(calls[0].url, /\/api\/projects\/project%20one\/player-runs$/);
  assert.equal(calls[0].options.headers["Idempotency-Key"], "player-key");
  assert.equal(calls[0].options.headers["X-Trace-Id"], "trace-1");
  assert.deepEqual(JSON.parse(calls[0].options.body), request());
});
