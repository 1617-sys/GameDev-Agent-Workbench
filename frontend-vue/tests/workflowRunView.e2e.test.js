import test from "node:test";
import assert from "node:assert/strict";
import { createWorkflowApi } from "../src/api/workflowApi.js";
import { createWorkflowRunStore } from "../src/stores/workflowRunStore.js";
import { navigateToWorkflowRun, workflowRunUuidFromPath } from "../src/router/workflowRoute.js";

test("submit response navigates only with the server workflowRunUuid", async () => {
  const calls = [];
  const api = createWorkflowApi(async (path, options) => { calls.push({ path, options }); return { workflowRunUuid: "server-run" }; });
  const request = { workflowKey: "GAME_GENERATE", idea: "idea", context: null };
  const response = await api.submit("project", request, "same-pending-key");
  const history = { pushState: (_state, _title, path) => { history.path = path; } };
  navigateToWorkflowRun(response.workflowRunUuid, history);
  assert.equal(calls[0].path, "/api/v1/projects/project/workflow-runs");
  assert.equal(calls[0].options.method, "POST");
  assert.deepEqual(calls[0].options.body, request);
  assert.equal(history.path, "/workflow-runs/server-run");
  assert.equal(calls[0].options.headers["Idempotency-Key"], "same-pending-key");
  assert.equal(workflowRunUuidFromPath(history.path), "server-run");
});

test("run detail smoke keeps server snapshots authoritative after commands", async () => {
  let reads = 0;
  let snapshot = { workflowRunUuid: "r1", status: "RUNNING", allowedActions: ["cancel"], lastSequence: 1, steps: [{ stepKey: "design", status: "RUNNING" }], artifacts: [] };
  const store = createWorkflowRunStore({ api: {
    getRun: async () => { reads += 1; return snapshot; },
    cancel: async () => {
      snapshot = { ...snapshot, status: "CANCELED", allowedActions: [], lastSequence: 2 };
      return { workflowRunUuid: "r1", status: "CANCELED", attempt: 1, reused: false };
    },
    retry: async () => snapshot,
    eventsUrl: () => ""
  } });
  await store.loadRun("r1");
  await store.cancel("r1");
  assert.equal(reads, 2);
  assert.equal(store.ensure("r1").snapshot.status, "CANCELED");
  assert.deepEqual(store.ensure("r1").snapshot.allowedActions, []);
  assert.equal(store.ensure("r1").steps[0].stepKey, "design");
});

test("artifact detail endpoint uses the server-issued artifact UUID", async () => {
  let request;
  const api = createWorkflowApi(async (path, options) => {
    request = { path, options };
    return { artifactUuid: "artifact/one", content: "{}" };
  });

  await api.getArtifact("artifact/one");

  assert.equal(request.path, "/api/artifacts/artifact%2Fone");
  assert.equal(request.options, undefined);
});
