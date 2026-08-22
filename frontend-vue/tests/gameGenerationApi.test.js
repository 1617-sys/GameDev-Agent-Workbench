import test from "node:test";
import assert from "node:assert/strict";
import { gameGenerationApi } from "../src/shared/api/gameGeneration.js";

test("uses project-scoped V5 compile and generation endpoints", async () => {
  const originalWindow = globalThis.window;
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.window = { setTimeout, clearTimeout, sessionStorage: { getItem: () => "token" } };
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    return { ok: true, status: 200, json: async () => ({ code: 0, data: { runUuid: "run-1" } }) };
  };
  try {
    await gameGenerationApi.capabilities();
    await gameGenerationApi.compile("project one", { specVersion: "0.1" });
    await gameGenerationApi.author("project one", "生成一个森林收集游戏", { specVersion: "0.1" });
    await gameGenerationApi.create("project one", { archetype: "arcade_collect" }, "generation-key");
    await gameGenerationApi.get("project one", "run/1");
    await gameGenerationApi.build("project one", "run/1", 3);
    await gameGenerationApi.approve("project one", "run/1", "APPROVED", "人工试玩通过", "approval-key");
    await gameGenerationApi.release("project one", "run/1", 5);
  } finally {
    globalThis.window = originalWindow;
    globalThis.fetch = originalFetch;
  }
  assert.match(calls[0].url, /\/api\/v5\/gamespec\/capabilities$/);
  assert.match(calls[1].url, /\/api\/v5\/projects\/project%20one\/gamespec\/compile$/);
  assert.deepEqual(JSON.parse(calls[1].options.body), { spec: { specVersion: "0.1" } });
  assert.match(calls[2].url, /\/api\/v5\/projects\/project%20one\/gamespec\/author$/);
  assert.equal(calls[3].options.headers["Idempotency-Key"], "generation-key");
  assert.match(calls[4].url, /generation-runs\/run%2F1$/);
  assert.match(calls[5].url, /generation-runs\/run%2F1\/build\?expectedVersion=3$/);
  assert.equal(calls[6].options.headers["Idempotency-Key"], "approval-key");
  assert.deepEqual(JSON.parse(calls[6].options.body), { decision: "APPROVED", reason: "人工试玩通过" });
  assert.match(calls[7].url, /generation-runs\/run%2F1\/release\?expectedVersion=5$/);
});
