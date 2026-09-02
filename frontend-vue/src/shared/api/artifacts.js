import { apiRequest } from "./http.js";

const root = projectUuid => `/api/projects/${encodeURIComponent(projectUuid)}/artifacts`;

export const artifactsApi = {
  list: projectUuid => apiRequest(root(projectUuid)),
  detail: (projectUuid, artifactUuid) => apiRequest(`${root(projectUuid)}/${encodeURIComponent(artifactUuid)}`),
  globalDetail: artifactUuid => apiRequest(`/api/artifacts/${encodeURIComponent(artifactUuid)}`)
};

export function artifactPage(items, { type = "", page = 0, size = 20 } = {}) {
  const filtered = (Array.isArray(items) ? items : []).filter(item => !type || item.artifactType === type);
  const safeSize = Math.max(1, Number(size) || 20);
  const safePage = Math.max(0, Number(page) || 0);
  return {
    items: filtered.slice(safePage * safeSize, (safePage + 1) * safeSize),
    page: safePage,
    size: safeSize,
    total: filtered.length,
    totalPages: filtered.length ? Math.ceil(filtered.length / safeSize) : 0
  };
}
