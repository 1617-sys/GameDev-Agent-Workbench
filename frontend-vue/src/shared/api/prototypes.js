import { apiRequest } from "./http.js";

const base = (projectUuid) => `/api/projects/${encodeURIComponent(projectUuid)}/prototype-versions`;

export const prototypesApi = {
  list: (projectUuid) => apiRequest(base(projectUuid)),
  get: (projectUuid, versionUuid) => apiRequest(`${base(projectUuid)}/${encodeURIComponent(versionUuid)}`),
  create: (projectUuid, artifactUuid, idempotencyKey) => apiRequest(base(projectUuid), {
    method: "POST", body: { artifactUuid }, headers: { "Idempotency-Key": idempotencyKey }
  }),
  tune: (projectUuid, parentVersionUuid, body, idempotencyKey) => apiRequest(
    `${base(projectUuid)}/${encodeURIComponent(parentVersionUuid)}/tune`,
    { method: "POST", body, headers: { "Idempotency-Key": idempotencyKey } }
  ),
  compare: (projectUuid, left, right) => apiRequest(
    `${base(projectUuid)}/compare?left=${encodeURIComponent(left)}&right=${encodeURIComponent(right)}`
  ),
  approve: (projectUuid, versionUuid, body, idempotencyKey) => apiRequest(
    `${base(projectUuid)}/${encodeURIComponent(versionUuid)}/approval`,
    { method: "POST", body, headers: { "Idempotency-Key": idempotencyKey } }
  )
};
