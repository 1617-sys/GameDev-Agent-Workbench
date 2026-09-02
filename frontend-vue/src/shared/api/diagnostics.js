import { apiRequest } from "./http.js";
export const diagnosticsApi = { health: () => apiRequest("/api/health", { auth: false }) };
