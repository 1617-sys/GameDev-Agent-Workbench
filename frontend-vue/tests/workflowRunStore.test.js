import test from "node:test";
import assert from "node:assert/strict";
import { createWorkflowRunStore } from "../src/stores/workflowRunStore.js";
import { workflowRunUuidFromPath } from "../src/router/workflowRoute.js";
import { createHttpClient } from "../src/api/httpClient.js";

test("snapshot wins and duplicate or stale events are ignored", () => {
  const store = createWorkflowRunStore({ api: { getRun: async () => ({}), eventsUrl: () => "" }, eventSourceFactory: () => ({ addEventListener() {}, close() {} }) });
  store.applySnapshot("run", { workflowRunUuid: "run", lastSequence: 4, steps: [], artifacts: [] });
  assert.equal(store.applyEvent("run", { sequence: 4 }), false);
  assert.equal(store.applyEvent("run", { sequence: 3 }), false);
  assert.equal(store.applyEvent("run", { sequence: 5 }), true);
});
test("route adapter restores encoded workflow uuid", () => assert.equal(workflowRunUuidFromPath("/workflow-runs/a%2Fb"), "a/b"));
test("http client serializes request DTOs without persisting credentials", async () => {
  let request;
  const http = createHttpClient({ getToken: () => "memory-token", fetchImpl: async (_url, options) => { request = options; return { ok: true, status: 202, json: async () => ({ code: 0, data: { ok: true } }) }; } });
  await http("/api/v1/projects/p/workflow-runs", { method: "POST", body: { workflowKey: "GAME_GENERATE" } });
  assert.equal(request.body, '{"workflowKey":"GAME_GENERATE"}');
  assert.equal(request.headers.Authorization, "Bearer memory-token");
});
