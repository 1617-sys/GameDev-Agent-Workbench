import { apiRequest } from "./http.js";
export const usersApi = {
  list: filters => apiRequest(`/api/admin/users?${new URLSearchParams(filters).toString()}`),
  update: (userId, body) => apiRequest(`/api/admin/users/${encodeURIComponent(userId)}`, { method: "PATCH", body }),
  audits: userId => apiRequest(`/api/admin/users/${encodeURIComponent(userId)}/audits`)
};
