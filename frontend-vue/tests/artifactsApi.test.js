import test from "node:test";
import assert from "node:assert/strict";
import { artifactsApi, artifactPage } from "../src/shared/api/artifacts.js";

test("artifact pagination filters without inventing records", () => {
  const items = [
    { artifactUuid: "a-1", artifactType: "GAME_CONFIG" },
    { artifactUuid: "a-2", artifactType: "REPORT" },
    { artifactUuid: "a-3", artifactType: "GAME_CONFIG" }
  ];
  assert.deepEqual(artifactPage(items, { type: "GAME_CONFIG", page: 0, size: 1 }), { items: [items[0]], page: 0, size: 1, total: 2, totalPages: 2 });
  assert.deepEqual(artifactPage([], { page: 0, size: 20 }), { items: [], page: 0, size: 20, total: 0, totalPages: 0 });
});

test("artifact API encodes project and artifact UUIDs and preserves errors", async () => {
  const originalWindow = globalThis.window; const originalFetch = globalThis.fetch; const calls = [];
  globalThis.window = { setTimeout, clearTimeout, sessionStorage: { getItem: () => "token" } };
  globalThis.fetch = async (url) => { calls.push(url); return { ok: false, status: 403, json: async () => ({ code: 40301, message: "Forbidden artifact access" }) }; };
  try { await assert.rejects(artifactsApi.detail("project one", "artifact/1"), /Forbidden artifact access/); }
  finally { globalThis.window = originalWindow; globalThis.fetch = originalFetch; }
  assert.match(calls[0], /projects\/project%20one\/artifacts\/artifact%2F1$/);
});

test("artifact API exposes the workflow-global artifact lookup", async () => {
  const originalWindow = globalThis.window; const originalFetch = globalThis.fetch; const calls = [];
  globalThis.window = { setTimeout, clearTimeout, sessionStorage: { getItem: () => "token" } };
  globalThis.fetch = async (url) => { calls.push(url); return { ok: true, status: 200, json: async () => ({ code: 0, data: {} }) }; };
  try { await artifactsApi.globalDetail("artifact/1"); }
  finally { globalThis.window = originalWindow; globalThis.fetch = originalFetch; }
  assert.match(calls[0], /\/api\/artifacts\/artifact%2F1$/);
});
