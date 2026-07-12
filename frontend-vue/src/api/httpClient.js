export class ApiError extends Error {
  constructor(message, { status = 0, code = "NETWORK" } = {}) { super(message); this.status = status; this.code = code; }
}

export function createHttpClient({ baseUrl = localStorage.getItem("gaw.baseUrl") || "http://localhost:8080", getToken = () => localStorage.getItem("gaw.token") || "", fetchImpl = fetch, timeoutMs = 15000 } = {}) {
  return async function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const headers = { Accept: "application/json", ...(options.headers || {}) };
      const token = getToken();
      if (token) headers.Authorization = `Bearer ${token}`;
      if (options.body) headers["Content-Type"] = "application/json";
      const response = await fetchImpl(`${baseUrl}${path}`, { ...options, headers, signal: controller.signal });
      const payload = response.status === 204 ? null : await response.json().catch(() => null);
      if (!response.ok || (payload && payload.code !== 0)) throw new ApiError(payload?.message || `HTTP ${response.status}`, { status: response.status, code: payload?.code || String(response.status) });
      return payload?.data ?? payload;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(error.name === "AbortError" ? "Request timed out" : "Network request failed");
    } finally { clearTimeout(timeout); }
  };
}
