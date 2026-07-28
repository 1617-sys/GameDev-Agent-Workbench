export const SESSION_TOKEN_KEY = "gameflow.session";

export class ApiError extends Error {
  constructor(message, { status = 0, code = "NETWORK" } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

let unauthorizedHandler = () => {};

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === "function" ? handler : () => {};
}

export function readToken() {
  try { return window.sessionStorage.getItem(SESSION_TOKEN_KEY) || ""; } catch { return ""; }
}

export async function apiRequest(path, options = {}) {
  const baseUrl = import.meta.env?.VITE_API_BASE_URL || "http://127.0.0.1:8080";
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), options.timeoutMs || 15_000);
  try {
    const headers = { Accept: "application/json", ...(options.headers || {}) };
    const token = options.auth === false ? "" : readToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    const multipart = typeof FormData !== "undefined" && options.body instanceof FormData;
    const body = options.body && typeof options.body !== "string" && !multipart
      ? JSON.stringify(options.body)
      : options.body;
    if (body && !multipart) headers["Content-Type"] = "application/json";
    const response = await fetch(`${baseUrl}${path}`, { ...options, headers, body, signal: controller.signal });
    const payload = response.status === 204 ? null : await response.json().catch(() => null);
    if (response.status === 401) unauthorizedHandler();
    if (!response.ok || (payload && payload.code !== 0)) {
      throw new ApiError(payload?.message || `请求失败（HTTP ${response.status}）`, {
        status: response.status,
        code: payload?.code || String(response.status)
      });
    }
    return payload?.data ?? payload;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(error.name === "AbortError" ? "请求超时，请稍后重试" : "无法连接服务，请检查服务状态");
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function apiDownload(path, options = {}) {
  const baseUrl = import.meta.env?.VITE_API_BASE_URL || "http://127.0.0.1:8080";
  const { timeoutMs = 30_000, auth = true, fallbackFilename = "prototype.zip", ...fetchOptions } = options;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers = { Accept: "application/zip", ...(options.headers || {}) };
    const token = auth ? readToken() : "";
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${baseUrl}${path}`, { ...fetchOptions, headers, signal: controller.signal });
    if (response.status === 401) unauthorizedHandler();
    if (!response.ok) {
      const payload = await response.json().catch(() => null);
      throw new ApiError(payload?.message || `下载失败（HTTP ${response.status}）`, {
        status: response.status,
        code: payload?.code || String(response.status)
      });
    }
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.toLowerCase().includes("application/zip")) {
      throw new ApiError("下载响应不是有效的 ZIP 文件", {
        status: response.status,
        code: "DOWNLOAD_INVALID_CONTENT"
      });
    }
    return {
      blob: await response.blob(),
      filename: downloadFilename(response.headers.get("content-disposition"), fallbackFilename)
    };
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(error.name === "AbortError" ? "下载超时，请稍后重试" : "无法下载文件，请检查服务状态");
  } finally {
    window.clearTimeout(timeout);
  }
}

function downloadFilename(disposition, fallback = "prototype.zip") {
  const value = disposition || "";
  const encoded = value.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plain = value.match(/filename="?([^";]+)"?/i)?.[1];
  let filename = plain || fallback;
  if (encoded) {
    try { filename = decodeURIComponent(encoded); } catch { filename = fallback; }
  }
  filename = filename.replace(/[\\/\u0000-\u001f\u007f]/g, "_").trim();
  return filename && filename.length <= 160 ? filename : "prototype.zip";
}

export function openEventStream(path, { lastEventId = 0, onEvent, onError } = {}) {
  const baseUrl = import.meta.env?.VITE_API_BASE_URL || "http://127.0.0.1:8080";
  const controller = new AbortController();
  let closed = false;

  void (async () => {
    try {
      const headers = { Accept: "text/event-stream" };
      const token = readToken();
      if (token) headers.Authorization = `Bearer ${token}`;
      if (Number(lastEventId) > 0) headers["Last-Event-ID"] = String(lastEventId);
      const response = await fetch(`${baseUrl}${path}`, { headers, signal: controller.signal });
      if (response.status === 401) unauthorizedHandler();
      if (!response.ok || !response.body) throw new ApiError("实时连接失败", { status: response.status });
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (!closed) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const frames = buffer.split(/\r?\n\r?\n/);
        buffer = frames.pop() || "";
        for (const frame of frames) {
          const lines = frame.split(/\r?\n/);
          const type = lines.find((line) => line.startsWith("event:"))?.slice(6).trim() || "message";
          const data = lines.filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trimStart()).join("\n");
          if (data) onEvent?.({ type, data });
        }
      }
      if (!closed) onError?.(new ApiError("实时连接已断开"));
    } catch (error) {
      if (!closed && error.name !== "AbortError") onError?.(error);
    }
  })();

  return { close: () => { closed = true; controller.abort(); } };
}
