import { apiRequest } from "./http.js";

const enc = encodeURIComponent;
export const telemetryApi = {
  createSession: (projectUuid, versionUuid) => apiRequest(`/api/projects/${enc(projectUuid)}/prototype-versions/${enc(versionUuid)}/playtest-sessions`, { method: "POST", body: {} }),
  ingest: (projectUuid, sessionUuid, batch) => apiRequest(`/api/projects/${enc(projectUuid)}/playtest-sessions/${enc(sessionUuid)}/events`, { method: "POST", body: batch }),
  session: (projectUuid, sessionUuid) => apiRequest(`/api/projects/${enc(projectUuid)}/playtest-sessions/${enc(sessionUuid)}`),
  metrics: (projectUuid, versionUuid) => apiRequest(`/api/projects/${enc(projectUuid)}/prototype-versions/${enc(versionUuid)}/playtest-metrics`),
  compare: (projectUuid, left, right) => apiRequest(`/api/projects/${enc(projectUuid)}/playtest-metrics/compare?left=${enc(left)}&right=${enc(right)}`),
  suggest: (projectUuid, versionUuid, key) => apiRequest(`/api/projects/${enc(projectUuid)}/prototype-versions/${enc(versionUuid)}/balance-suggestions`, { method: "POST", headers: { "Idempotency-Key": key }, body: {} })
};

const uuid = () => crypto.randomUUID();
export async function createTelemetryReporter(projectUuid, versionUuid, api = telemetryApi) {
  const session = await api.createSession(projectUuid, versionUuid);
  let sequence = 0, queue = [], timer = null, chain = Promise.resolve(), ended = false;
  const flush = () => {
    while (queue.length) {
      const events = queue.splice(0, 50); const batch = { batchUuid: uuid(), events };
      chain = chain.then(() => api.ingest(projectUuid, session.sessionUuid, batch));
    }
    return chain;
  };
  const emit = (type, clientElapsedMs, payload = {}) => {
    if (ended) return; sequence += 1;
    queue.push({ eventUuid: uuid(), sequence, type, clientElapsedMs: Math.max(0, Math.min(1800000, Math.round(clientElapsedMs || 0))), payload });
    if (type === "SESSION_ENDED") ended = true;
    clearTimeout(timer); timer = setTimeout(flush, 200);
    if (queue.length >= 50 || ended) void flush();
  };
  return { sessionUuid: session.sessionUuid, emit, flush };
}
