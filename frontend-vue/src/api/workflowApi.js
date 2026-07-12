export function createWorkflowApi(http) {
  const base = (uuid) => `/api/v1/workflow-runs/${encodeURIComponent(uuid)}`;
  return {
    getRun: (uuid) => http(base(uuid)),
    getSteps: (uuid) => http(`${base(uuid)}/steps`),
    getArtifacts: (uuid) => http(`${base(uuid)}/artifacts`),
    cancel: (uuid) => http(`${base(uuid)}/cancel`, { method: "POST" }),
    retry: (uuid) => http(`${base(uuid)}/retry`, { method: "POST" }),
    eventsUrl: (uuid) => base(uuid) + "/events"
  };
}
