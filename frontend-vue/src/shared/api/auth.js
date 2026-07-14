import { apiRequest } from "./http";

export const authApi = {
  register: (body) => apiRequest("/api/auth/register", { method: "POST", body, auth: false }),
  login: (body) => apiRequest("/api/auth/login", { method: "POST", body, auth: false }),
  me: () => apiRequest("/api/auth/me")
};
