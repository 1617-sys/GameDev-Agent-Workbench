import { apiRequest } from "./http.js";

const root = (projectUuid) => `/api/projects/${encodeURIComponent(projectUuid)}/director-runs`;

export const directorApi = {
  create: (projectUuid, body, idempotencyKey) => apiRequest(root(projectUuid), {
    method: "POST", body, headers: { "Idempotency-Key": idempotencyKey }
  }),
  get: (projectUuid, runUuid) => apiRequest(`${root(projectUuid)}/${encodeURIComponent(runUuid)}`),
  cancel: (projectUuid, runUuid, expectedVersion) => apiRequest(`${root(projectUuid)}/${encodeURIComponent(runUuid)}/cancel?expectedVersion=${encodeURIComponent(expectedVersion)}`, { method: "POST" }),
  approve: (projectUuid, versionUuid, decision, reason, idempotencyKey) => apiRequest(`/api/projects/${encodeURIComponent(projectUuid)}/prototype-versions/${encodeURIComponent(versionUuid)}/approval`, {
    method: "POST", body: { decision, reason }, headers: { "Idempotency-Key": idempotencyKey }
  })
};
