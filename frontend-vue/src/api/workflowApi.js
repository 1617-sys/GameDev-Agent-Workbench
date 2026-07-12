export function createWorkflowApi(http) {
  const base = (uuid) => `/api/v1/workflow-runs/${encodeURIComponent(uuid)}`;
  return {
    submit: (projectUuid, request, idempotencyKey) => http(`/api/v1/projects/${encodeURIComponent(projectUuid)}/workflow-runs`, { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: request }),
    getRun: (uuid) => http(base(uuid)),
    getSteps: (uuid) => http(`${base(uuid)}/steps`),
    getArtifacts: (uuid) => http(`${base(uuid)}/artifacts`),
    cancel: (uuid) => http(`${base(uuid)}/cancel`, { method: "POST" }),
    retry: (uuid) => http(`${base(uuid)}/retry`, { method: "POST" }),
    eventsUrl: (uuid) => base(uuid) + "/events"
  };
}
