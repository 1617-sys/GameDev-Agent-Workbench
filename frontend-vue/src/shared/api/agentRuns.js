import { apiRequest } from "./http.js";
import { agentRunsQuery } from "../../features/admin/agentRunsAdmin.js";
export const agentRunsApi = {
  list: filters => apiRequest(`/api/agent/runs?${agentRunsQuery(filters)}`),
  detail: runUuid => apiRequest(`/api/agent/runs/${encodeURIComponent(runUuid)}`),
  create: (body, idempotencyKey) => apiRequest("/api/agent/run", { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body })
};
