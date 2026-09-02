import { apiRequest } from "./http.js";
export const dashboardApi = {
  projects: () => apiRequest("/api/dashboard/projects/summary"),
  agentTypes: () => apiRequest("/api/dashboard/projects/selectAgentType")
};
