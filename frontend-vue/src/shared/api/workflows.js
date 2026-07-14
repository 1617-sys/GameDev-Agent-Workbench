import { apiRequest, openEventStream } from "./http";

const runPath = (uuid) => `/api/v1/workflow-runs/${encodeURIComponent(uuid)}`;

export const workflowsApi = {
  projectRuns: (projectUuid) => apiRequest(
    `/api/v1/projects/${encodeURIComponent(projectUuid)}/workflow-runs`
  ),
  submit: (projectUuid, body, idempotencyKey) => apiRequest(
    `/api/v1/projects/${encodeURIComponent(projectUuid)}/workflow-runs`,
    { method: "POST", body, headers: { "Idempotency-Key": idempotencyKey } }
  ),
  run: (uuid) => apiRequest(runPath(uuid)),
  steps: (uuid) => apiRequest(`${runPath(uuid)}/steps`),
  artifacts: (uuid) => apiRequest(`${runPath(uuid)}/artifacts`),
  artifact: (uuid) => apiRequest(`/api/artifacts/${encodeURIComponent(uuid)}`),
  cancel: (uuid) => apiRequest(`${runPath(uuid)}/cancel`, { method: "POST" }),
  retry: (uuid) => apiRequest(`${runPath(uuid)}/retry`, { method: "POST" }),
  subscribe: (uuid, options) => openEventStream(`${runPath(uuid)}/events`, options)
};
