import { apiRequest } from "./http";

const projectPath = (uuid) => `/api/projects/${encodeURIComponent(uuid)}`;

export const projectsApi = {
  list: () => apiRequest("/api/projects"),
  create: (body) => apiRequest("/api/projects", { method: "POST", body }),
  get: (uuid) => apiRequest(projectPath(uuid)),
  update: (uuid, body) => apiRequest(projectPath(uuid), { method: "PUT", body })
};
