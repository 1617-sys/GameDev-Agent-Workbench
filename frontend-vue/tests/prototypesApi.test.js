import test from "node:test";
import assert from "node:assert/strict";
import { prototypesApi } from "../src/shared/api/prototypes.js";

test("uses project-scoped immutable version endpoints and idempotency", async () => {
  const originalWindow = globalThis.window;
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.window = {
    setTimeout,
    clearTimeout,
    sessionStorage: { getItem: () => "token" }
  };
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    return { ok: true, status: 200, json: async () => ({ code: 0, data: { ok: true } }) };
  };
  try {
    await prototypesApi.list("project one");
    await prototypesApi.tune("project one", "version/1", { playerSpeed: 240 }, "tune-key");
    await prototypesApi.compare("project one", "left", "right");
    await prototypesApi.approve("project one", "version/1", { decision: "APPROVED", reason: "ok" }, "approval-key");
  } finally {
    globalThis.window = originalWindow;
    globalThis.fetch = originalFetch;
  }
  assert.match(calls[0].url, /\/api\/projects\/project%20one\/prototype-versions$/);
  assert.match(calls[1].url, /\/version%2F1\/tune$/);
  assert.equal(calls[1].options.headers["Idempotency-Key"], "tune-key");
  assert.equal(JSON.parse(calls[1].options.body).playerSpeed, 240);
  assert.match(calls[2].url, /compare\?left=left&right=right$/);
  assert.match(calls[3].url, /\/version%2F1\/approval$/);
  assert.equal(calls[3].options.headers["Idempotency-Key"], "approval-key");
});
