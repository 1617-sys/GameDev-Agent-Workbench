import { apiRequest } from "./http.js";

export const promptOpsApi = {
  list({ pageNum = 1, pageSize = 20, agentType = "", status = "" } = {}) {
    const query = new URLSearchParams({ pageNum: String(pageNum), pageSize: String(pageSize) });
    if (agentType) query.set("agentType", agentType);
    if (status) query.set("status", status);
    return apiRequest(`/api/promptTemplate?${query}`);
  },
  detail(templateUuid) {
    return apiRequest(`/api/promptTemplate/get?templateUuid=${encodeURIComponent(templateUuid)}`);
  },
  update(templateUuid, body) {
    return apiRequest(`/api/promptTemplate/${encodeURIComponent(templateUuid)}`, { method: "PUT", body });
  }
};
