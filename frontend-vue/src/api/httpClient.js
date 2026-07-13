export class ApiError extends Error {
  constructor(message, { status = 0, code = "NETWORK" } = {}) { super(message); this.status = status; this.code = code; }
}

export function createHttpClient({ baseUrl = "http://localhost:8080", getToken = () => "", fetchImpl = fetch, timeoutMs = 15000 } = {}) {
  async function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const headers = { Accept: "application/json", ...(options.headers || {}) };
      const token = options.auth === false ? "" : getToken();
      if (token) headers.Authorization = `Bearer ${token}`;
      const multipart = typeof FormData !== "undefined" && options.body instanceof FormData;
      const body = options.body && typeof options.body !== "string" && !multipart
        ? JSON.stringify(options.body)
        : options.body;
      if (body && !multipart) headers["Content-Type"] = "application/json";
      const response = await fetchImpl(`${baseUrl}${path}`, { ...options, body, headers, signal: controller.signal });
      const payload = response.status === 204 ? null : await response.json().catch(() => null);
      if (!response.ok || (payload && payload.code !== 0)) throw new ApiError(payload?.message || `HTTP ${response.status}`, { status: response.status, code: payload?.code || String(response.status) });
      return payload?.data ?? payload;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(error.name === "AbortError" ? "Request timed out" : "Network request failed");
    } finally { clearTimeout(timeout); }
  }

  request.openSse = (path, { lastEventId } = {}) => {
    const listeners = new Map();
    const controller = new AbortController();
    let closed = false;
    const source = {
      addEventListener: (type, listener) => listeners.set(type, listener),
      close: () => { closed = true; controller.abort(); },
      onerror: null
    };
    const emit = (type, data) => listeners.get(type)?.({ data });
    const fail = (status = 0) => { if (!closed) source.onerror?.({ status }); };
    const parse = (chunk) => {
      chunk.split(/\r?\n\r?\n/).forEach((frame) => {
        const lines = frame.split(/\r?\n/);
        const event = lines.find((line) => line.startsWith("event:"))?.slice(6).trim() || "message";
        const data = lines.filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trimStart()).join("\n");
        if (data) emit(event, data);
      });
    };
    void (async () => {
      try {
        const headers = { Accept: "text/event-stream" };
        const token = getToken();
        if (token) headers.Authorization = `Bearer ${token}`;
        if (Number.isSafeInteger(lastEventId) && lastEventId >= 0) headers["Last-Event-ID"] = String(lastEventId);
        const response = await fetchImpl(`${baseUrl}${path}`, { headers, signal: controller.signal });
        if (!response.ok || !response.body) { fail(response.status); return; }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffered = "";
        while (!closed) {
          const { done, value } = await reader.read();
          if (done) break;
          buffered += decoder.decode(value, { stream: true });
          const frames = buffered.split(/\r?\n\r?\n/);
          buffered = frames.pop() || "";
          frames.forEach(parse);
        }
        if (buffered.trim()) parse(buffered);
        if (!closed) fail();
      } catch (error) { if (!closed && error.name !== "AbortError") fail(); }
    })();
    return source;
  };
  return request;
}
