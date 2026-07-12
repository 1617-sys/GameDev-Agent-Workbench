import test from "node:test";
import assert from "node:assert/strict";
import { createWorkflowRunStore } from "../src/stores/workflowRunStore.js";
import { workflowRunUuidFromPath } from "../src/router/workflowRoute.js";

test("snapshot wins and duplicate or stale events are ignored", () => {
  const store = createWorkflowRunStore({ api: { getRun: async () => ({}), eventsUrl: () => "" }, eventSourceFactory: () => ({ addEventListener() {}, close() {} }) });
  store.applySnapshot("run", { workflowRunUuid: "run", lastSequence: 4, steps: [], artifacts: [] });
  assert.equal(store.applyEvent("run", { sequence: 4 }), false);
  assert.equal(store.applyEvent("run", { sequence: 3 }), false);
  assert.equal(store.applyEvent("run", { sequence: 5 }), true);
});
test("route adapter restores encoded workflow uuid", () => assert.equal(workflowRunUuidFromPath("/workflow-runs/a%2Fb"), "a/b"));
