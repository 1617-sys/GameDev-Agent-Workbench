import test from "node:test";
import assert from "node:assert/strict";
import { createWorkflowRunStore } from "../src/stores/workflowRunStore.js";
import { workflowRunUuidFromPath } from "../src/router/workflowRoute.js";
import { createHttpClient } from "../src/api/httpClient.js";

test("snapshot wins and duplicate or stale events are ignored", () => {
  const store = createWorkflowRunStore({ api: { getRun: async () => ({}), eventsUrl: () => "" }, eventSourceFactory: () => ({ addEventListener() {}, close() {} }) });
  store.applySnapshot("run", { workflowRunUuid: "run", lastSequence: 4, steps: [], artifacts: [] });
  assert.equal(store.applyEvent("run", { workflowRunUuid: "run", sequence: 4 }), "stale");
  assert.equal(store.applyEvent("run", { workflowRunUuid: "run", sequence: 3 }), "stale");
  assert.equal(store.applyEvent("run", { workflowRunUuid: "run", sequence: 5 }), "applied");
});
test("route adapter restores encoded workflow uuid", () => assert.equal(workflowRunUuidFromPath("/workflow-runs/a%2Fb"), "a/b"));
test("http client serializes request DTOs without persisting credentials", async () => {
  let request;
  const http = createHttpClient({ getToken: () => "memory-token", fetchImpl: async (_url, options) => { request = options; return { ok: true, status: 202, json: async () => ({ code: 0, data: { ok: true } }) }; } });
  await http("/api/v1/projects/p/workflow-runs", { method: "POST", body: { workflowKey: "GAME_GENERATE" } });
  assert.equal(request.body, '{"workflowKey":"GAME_GENERATE"}');
  assert.equal(request.headers.Authorization, "Bearer memory-token");
});
test("401 responses notify the session owner without exposing the token", async () => {
  let unauthorized = 0;
  const http = createHttpClient({
    getToken: () => "memory-token",
    onUnauthorized: () => { unauthorized += 1; },
    fetchImpl: async () => ({ ok: false, status: 401, json: async () => ({ code: 401, message: "Unauthorized" }) })
  });

  await assert.rejects(http("/api/auth/me"), { status: 401 });
  assert.equal(unauthorized, 1);
});
test("SSE client keeps authorization and Last-Event-ID in headers, never in the URL", async () => {
  let request;
  const http = createHttpClient({ getToken: () => "memory-token", fetchImpl: async (url, options) => { request = { url, options }; return { ok: false, status: 401 }; } });
  http.openSse("/api/v1/workflow-runs/run/events", { lastEventId: 7 });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(request.url, "http://localhost:8080/api/v1/workflow-runs/run/events");
  assert.equal(request.options.headers.Authorization, "Bearer memory-token");
  assert.equal(request.options.headers["Last-Event-ID"], "7");
  assert.equal(request.url.includes("token"), false);
});

class FakeEventSource {
  constructor() { this.listeners = new Map(); this.closed = false; }
  addEventListener(type, listener) { this.listeners.set(type, listener); }
  emit(type, data) { return this.listeners.get(type)?.({ data: JSON.stringify(data) }); }
  close() { this.closed = true; }
}

const snapshot = (uuid, lastSequence = 1, status = "RUNNING") => ({ workflowRunUuid: uuid, status, lastSequence, allowedActions: [], steps: [{ stepKey: "design", stepOrder: 1, status: "PENDING", attempt: 1 }], artifacts: [] });

test("SSE lifecycle has one source per run and terminal events release it", async () => {
  const sources = [];
  const store = createWorkflowRunStore({ api: { getRun: async (uuid) => snapshot(uuid), eventsUrl: (uuid) => `/events/${uuid}` }, eventSourceFactory: (_url, options) => { const source = new FakeEventSource(); source.options = options; sources.push(source); return source; } });
  await store.open("run");
  assert.equal(sources.length, 1);
  assert.equal(sources[0].options.lastEventId, 1);
  store.connect("run");
  assert.equal(sources[0].closed, true);
  assert.equal(sources.length, 2);
  await sources[1].emit("run.terminal", { workflowRunUuid: "run", eventType: "run.terminal", sequence: 2, status: "SUCCESS" });
  assert.equal(store.ensure("run").snapshot.status, "SUCCESS");
  assert.equal(sources[1].closed, true);
});

test("SSE gap reloads the persistent snapshot and transient errors use bounded reconnect", async () => {
  const timers = [];
  let reads = 0;
  const source = new FakeEventSource();
  const store = createWorkflowRunStore({
    api: { getRun: async (uuid) => snapshot(uuid, ++reads), eventsUrl: () => "/events" },
    eventSourceFactory: () => source,
    reconnect: { maxAttempts: 1, baseDelayMs: 1, maxDelayMs: 1 },
    setTimeoutImpl: (callback) => { timers.push(callback); return timers.length; },
    clearTimeoutImpl: () => {}
  });
  await store.open("run");
  await source.emit("step.status-changed", { workflowRunUuid: "run", eventType: "step.status-changed", sequence: 3, stepKey: "design", status: "SUCCESS" });
  assert.equal(reads, 2);
  assert.equal(store.ensure("run").connectionState, "reconnecting");
  await timers.shift()();
  assert.equal(reads, 3);
  assert.equal(store.ensure("run").lastSequence, 3);
});

test("403 and 404 SSE failures stop reconnection and clear protected snapshots", async () => {
  const source = new FakeEventSource();
  const store = createWorkflowRunStore({ api: { getRun: async (uuid) => snapshot(uuid), eventsUrl: () => "/events" }, eventSourceFactory: () => source });
  await store.open("run");
  source.onerror({ status: 404 });
  assert.equal(store.ensure("run").connectionState, "forbidden");
  assert.equal(store.ensure("run").snapshot, null);
  assert.equal(store.ensure("run").retryTimer, null);
});

test("step events update only their step and cannot complete the workflow early", () => {
  const store = createWorkflowRunStore({ api: { getRun: async () => ({}), eventsUrl: () => "" }, eventSourceFactory: () => ({ addEventListener() {}, close() {} }) });
  store.applySnapshot("run", snapshot("run", 1, "RUNNING"));

  store.applyEvent("run", { workflowRunUuid: "run", eventType: "step.status-changed", sequence: 2, stepKey: "design", status: "SUCCESS" });

  assert.equal(store.ensure("run").snapshot.status, "RUNNING");
  assert.equal(store.ensure("run").steps[0].status, "SUCCESS");
});

test("network snapshot failures retain an understandable error and can recover from the server", async () => {
  let online = false;
  const store = createWorkflowRunStore({
    api: {
      getRun: async () => {
        if (!online) throw new Error("Network request failed");
        return snapshot("run", 2, "SUCCESS");
      },
      eventsUrl: () => "/events"
    },
    eventSourceFactory: () => new FakeEventSource()
  });

  await store.open("run");
  assert.equal(store.ensure("run").snapshot, null);
  assert.equal(store.ensure("run").error.message, "Network request failed");

  online = true;
  await store.open("run");
  assert.equal(store.ensure("run").snapshot.status, "SUCCESS");
  assert.equal(store.ensure("run").error, null);
});
