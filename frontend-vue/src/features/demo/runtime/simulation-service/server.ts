import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { timingSafeEqual } from "node:crypto";
import { fileURLToPath } from "node:url";
import { SERVICE_PROTOCOL_VERSION, ServiceError, SessionManager } from "./sessionManager.ts";

const MAX_BODY_BYTES = 2 * 1024 * 1024;

function send(response: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "content-length": Buffer.byteLength(payload), "cache-control": "no-store" });
  response.end(payload);
}

async function body(request: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  let length = 0;
  for await (const chunk of request) {
    length += chunk.length;
    if (length > MAX_BODY_BYTES) throw new ServiceError(413, "REQUEST_TOO_LARGE", "Request body is too large");
    chunks.push(chunk);
  }
  try {
    const parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") throw new Error();
    return parsed;
  } catch {
    throw new ServiceError(400, "INVALID_JSON", "Request body must be a JSON object");
  }
}

function authorized(request: IncomingMessage, token: string): boolean {
  const supplied = String(request.headers["x-internal-token"] ?? "");
  if (!token || supplied.length !== token.length) return false;
  return timingSafeEqual(Buffer.from(supplied), Buffer.from(token));
}

export function createSimulationHttpServer(options: { token: string; manager?: SessionManager }) {
  if (options.token.length < 32) throw new Error("SIMULATION_SERVICE_INTERNAL_TOKEN must be at least 32 characters");
  const manager = options.manager ?? new SessionManager();
  const server = createServer(async (request, response) => {
    const correlationId = String(request.headers["x-correlation-id"] ?? "unknown").slice(0, 128);
    try {
      const url = new URL(request.url ?? "/", "http://simulation-service");
      if (request.method === "GET" && url.pathname === "/health") {
        send(response, 200, { protocolVersion: SERVICE_PROTOCOL_VERSION, status: "ok", service: "simulation-service" });
        return;
      }
      if (!authorized(request, options.token)) throw new ServiceError(401, "UNAUTHORIZED", "Unauthorized");
      if (request.method === "POST" && url.pathname === "/v1/sessions") {
        send(response, 201, manager.create(await body(request) as never));
        return;
      }
      const match = /^\/v1\/sessions\/([0-9a-f-]+)(?:\/(observation|steps))?$/.exec(url.pathname);
      if (!match) throw new ServiceError(404, "ROUTE_NOT_FOUND", "Route was not found");
      const [, sessionId, operation] = match;
      if (request.method === "GET" && operation === "observation") {
        send(response, 200, { protocolVersion: "simulation/1.0", sessionId, observation: manager.observe(sessionId) });
      } else if (request.method === "POST" && operation === "steps") {
        const input = await body(request);
        send(response, 200, { protocolVersion: "simulation/1.0", sessionId, stepResult: await manager.step(sessionId, input.action as never) });
      } else if (request.method === "DELETE" && !operation) {
        send(response, 200, manager.close(sessionId));
      } else {
        throw new ServiceError(405, "METHOD_NOT_ALLOWED", "Method is not allowed");
      }
    } catch (cause) {
      const error = cause instanceof ServiceError ? cause : new ServiceError(500, "INTERNAL_ERROR", "Internal service error");
      send(response, error.status, { protocolVersion: SERVICE_PROTOCOL_VERSION, correlationId, error: { code: error.code, message: error.message, retriable: error.retriable } });
    }
  });
  server.on("close", () => manager.clear());
  return { server, manager };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const token = process.env.SIMULATION_SERVICE_INTERNAL_TOKEN ?? "";
  const port = Number(process.env.SIMULATION_SERVICE_PORT ?? 8090);
  const manager = new SessionManager({
    ttlMs: Number(process.env.SIMULATION_SESSION_TTL_MS ?? 300_000),
    maxSessions: Number(process.env.SIMULATION_MAX_SESSIONS ?? 100)
  });
  const { server } = createSimulationHttpServer({ token, manager });
  server.listen(port, "0.0.0.0", () => console.log(`simulation-service listening on ${port}`));
  const shutdown = () => server.close(() => process.exit(0));
  process.once("SIGTERM", shutdown);
  process.once("SIGINT", shutdown);
}
