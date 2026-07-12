import test from "node:test";
import assert from "node:assert/strict";
import { createWorkflowApi } from "../src/api/workflowApi.js";
import { createWorkflowRunStore } from "../src/stores/workflowRunStore.js";
import { navigateToWorkflowRun, workflowRunUuidFromPath } from "../src/router/workflowRoute.js";

test("submit response navigates only with the server workflowRunUuid", async () => {
  const calls = [];
  const api = createWorkflowApi(async (path, options) => { calls.push({ path, options }); return { workflowRunUuid: "server-run" }; });
  const response = await api.submit("project", { workflowKey: "GAME_GENERATE", idea: "idea" }, "same-pending-key");
  const history = { pushState: (_state, _title, path) => { history.path = path; } };
  navigateToWorkflowRun(response.workflowRunUuid, history);
  assert.equal(history.path, "/workflow-runs/server-run");
  assert.equal(calls[0].options.headers["Idempotency-Key"], "same-pending-key");
  assert.equal(workflowRunUuidFromPath(history.path), "server-run");
});

test("run detail smoke keeps server snapshots authoritative after commands", async () => {
  let snapshot = { workflowRunUuid: "r1", status: "RUNNING", allowedActions: ["cancel"], lastSequence: 1, steps: [], artifacts: [] };
  const store = createWorkflowRunStore({ api: { getRun: async () => snapshot, cancel: async () => { snapshot = { ...snapshot, status: "CANCELED", allowedActions: [], lastSequence: 2 }; return snapshot; }, retry: async () => snapshot, eventsUrl: () => "" } });
  await store.loadRun("r1");
  await store.cancel("r1");
  assert.equal(store.ensure("r1").snapshot.status, "CANCELED");
  assert.deepEqual(store.ensure("r1").snapshot.allowedActions, []);
});
