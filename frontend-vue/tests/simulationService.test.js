import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { afterEach, test } from "node:test";
import { fileURLToPath } from "node:url";
import { createSimulation, hashCanonical } from "../src/features/demo/runtime/simulation/index.ts";
import { createSimulationHttpServer, SessionManager } from "../src/features/demo/runtime/simulation-service/index.ts";

const token = "test-simulation-token-at-least-32-characters";
const fixturePath = fileURLToPath(new URL("../../docs/requirements/v3/examples/game-config-2.0/valid-minimal.json", import.meta.url));
const servers = [];

afterEach(async () => {
  await Promise.all(servers.splice(0).map(({ server }) => new Promise((resolve) => server.close(resolve))));
});

function fixture() { return JSON.parse(readFileSync(fixturePath, "utf8")); }

function requestBody(overrides = {}) {
  const gameConfig = fixture();
  return {
    protocolVersion: "simulation/1.0",
    episodeId: "00000000-0000-4000-8000-000000000101",
    correlationId: "run-101",
    configDigest: hashCanonical(gameConfig),
    seed: 101,
    maxSteps: 100,
    observationPolicy: { kind: "FULL" },
    gameConfig,
    ...overrides
  };
}

async function service(manager = new SessionManager()) {
  const instance = createSimulationHttpServer({ token, manager });
  await new Promise((resolve) => instance.server.listen(0, "127.0.0.1", resolve));
  servers.push(instance);
  return { ...instance, baseUrl: `http://127.0.0.1:${instance.server.address().port}` };
}

async function call(baseUrl, path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: { "content-type": "application/json", "x-internal-token": token, ...(options.headers ?? {}) }
  });
  return { status: response.status, body: await response.json() };
}

test("creates, observes, advances and closes one bounded session", async () => {
  const { baseUrl, manager } = await service();
  const created = await call(baseUrl, "/v1/sessions", { method: "POST", body: JSON.stringify(requestBody()) });
  assert.equal(created.status, 201);
  assert.equal(created.body.observation.step, 0);
  const observed = await call(baseUrl, `/v1/sessions/${created.body.sessionId}/observation`);
  assert.equal(observed.body.observation.stateHash, created.body.observation.stateHash);
  const stepped = await call(baseUrl, `/v1/sessions/${created.body.sessionId}/steps`, { method: "POST", body: JSON.stringify({ action: { type: "WAIT" } }) });
  assert.equal(stepped.body.stepResult.step, 1);
  assert.equal(stepped.body.stepResult.previousStateHash, created.body.observation.stateHash);
  assert.equal(manager.size, 1);
  const closed = await call(baseUrl, `/v1/sessions/${created.body.sessionId}`, { method: "DELETE" });
  assert.equal(closed.body.closed, true);
  assert.equal(manager.size, 0);
  const closedAgain = await call(baseUrl, `/v1/sessions/${created.body.sessionId}`, { method: "DELETE" });
  assert.equal(closedAgain.body.closed, true);
  const afterClose = await call(baseUrl, `/v1/sessions/${created.body.sessionId}/steps`, { method: "POST", body: JSON.stringify({ action: { type: "WAIT" } }) });
  assert.equal(afterClose.status, 410);
  assert.equal(afterClose.body.error.code, "SESSION_CLOSED");
});

test("rejects invalid tokens and unknown or expired sessions without leaking internals", async () => {
  let now = 1_000;
  const { baseUrl } = await service(new SessionManager({ ttlMs: 1_000, now: () => now }));
  const unauthorized = await call(baseUrl, "/v1/sessions", { method: "POST", body: JSON.stringify(requestBody()), headers: { "x-internal-token": "wrong" } });
  assert.equal(unauthorized.status, 401);
  assert.deepEqual(Object.keys(unauthorized.body.error).sort(), ["code", "message", "retriable"]);
  const unknown = await call(baseUrl, "/v1/sessions/00000000-0000-4000-8000-000000000999/observation");
  assert.equal(unknown.status, 404);
  const created = await call(baseUrl, "/v1/sessions", { method: "POST", body: JSON.stringify(requestBody()) });
  now += 1_001;
  const expired = await call(baseUrl, `/v1/sessions/${created.body.sessionId}/observation`);
  assert.equal(expired.status, 410);
  assert.equal(expired.body.error.code, "SESSION_EXPIRED");
});

test("enforces capacity and rejects a concurrent operation on the same session", async () => {
  const { baseUrl, manager } = await service(new SessionManager({ maxSessions: 1 }));
  const created = await call(baseUrl, "/v1/sessions", { method: "POST", body: JSON.stringify(requestBody()) });
  const capacity = await call(baseUrl, "/v1/sessions", { method: "POST", body: JSON.stringify(requestBody({ episodeId: "episode-2" })) });
  assert.equal(capacity.status, 503);
  const first = manager.step(created.body.sessionId, { type: "WAIT" });
  assert.throws(() => manager.observe(created.body.sessionId), (error) => error.code === "SESSION_BUSY");
  await assert.rejects(manager.step(created.body.sessionId, { type: "WAIT" }), (error) => error.code === "SESSION_BUSY");
  await first;
});

test("matches direct SimulationCore state for identical config, seed and actions", async () => {
  const { baseUrl } = await service();
  const body = requestBody();
  const created = await call(baseUrl, "/v1/sessions", { method: "POST", body: JSON.stringify(body) });
  const actions = ["MOVE_RIGHT", "WAIT", "MOVE_DOWN"];
  let last;
  for (const type of actions) last = await call(baseUrl, `/v1/sessions/${created.body.sessionId}/steps`, { method: "POST", body: JSON.stringify({ action: { type } }) });
  const direct = createSimulation(body.gameConfig, body);
  for (const type of actions) direct.step({ type });
  assert.equal(last.body.stepResult.stateHash, direct.stateHash());
});

test("rejects step after the episode reaches a terminal state", async () => {
  const { baseUrl } = await service();
  const created = await call(baseUrl, "/v1/sessions", { method: "POST", body: JSON.stringify(requestBody({ maxSteps: 1 })) });
  await call(baseUrl, `/v1/sessions/${created.body.sessionId}/steps`, { method: "POST", body: JSON.stringify({ action: { type: "WAIT" } }) });
  const terminal = await call(baseUrl, `/v1/sessions/${created.body.sessionId}/steps`, { method: "POST", body: JSON.stringify({ action: { type: "WAIT" } }) });
  assert.equal(terminal.status, 409);
  assert.equal(terminal.body.error.code, "EPISODE_TERMINATED");
});
