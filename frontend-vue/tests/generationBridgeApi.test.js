import test from "node:test";
import assert from "node:assert/strict";
import { gameGenerationApi } from "../src/shared/api/gameGeneration.js";

test("bridge API preserves encoded source, idempotency key and structured source summary", async () => {
  const originalWindow = globalThis.window;
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.window = { setTimeout, clearTimeout, sessionStorage: { getItem: () => "token" } };
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    return { ok: true, status: 200, json: async () => ({ code: 0, data: {
      compatible: true,
      prototypeVersionUuid: "version-1",
      source: { runUuid: "run/1", sourceDigest: "source", runtimeIrDigest: "runtime", status: "RELEASED" }
    } }) };
  };
  try {
    await gameGenerationApi.prototypeCompatibility("project one", "run/1");
    const result = await gameGenerationApi.createPrototypeVersion("project one", "run/1", "bridge-key");
    assert.equal(result.source.runUuid, "run/1");
  } finally {
    globalThis.window = originalWindow;
    globalThis.fetch = originalFetch;
  }
  assert.match(calls[0].url, /project%20one\/generation-runs\/run%2F1\/prototype-version-compatibility$/);
  assert.match(calls[1].url, /run%2F1\/prototype-version$/);
  assert.equal(calls[1].options.headers["Idempotency-Key"], "bridge-key");
  assert.deepEqual(JSON.parse(calls[1].options.body), {});
});
