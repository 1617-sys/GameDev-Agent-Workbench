import { randomUUID } from "node:crypto";
import { validateGameConfig } from "../gameConfig.js";
import { createSimulation, hashCanonical, SIMULATION_PROTOCOL_VERSION } from "../simulation/index.ts";
import type { Action, GameConfig, Observation, SimulationCore, SimulationOptions, StepResult } from "../simulation/index.ts";

export const SERVICE_PROTOCOL_VERSION = "simulation-service/1.0" as const;

export class ServiceError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retriable: boolean;

  constructor(status: number, code: string, message: string, retriable = false) {
    super(message);
    this.status = status;
    this.code = code;
    this.retriable = retriable;
  }
}

export interface CreateSessionRequest extends SimulationOptions {
  correlationId: string;
  gameConfig: GameConfig;
}

interface SessionRecord {
  core: SimulationCore;
  episodeId: string;
  expiresAt: number;
  busy: boolean;
}

export interface SessionManagerOptions {
  ttlMs?: number;
  maxSessions?: number;
  now?: () => number;
  idFactory?: () => string;
}

export class SessionManager {
  readonly #sessions = new Map<string, SessionRecord>();
  readonly #gone = new Map<string, "SESSION_EXPIRED" | "SESSION_CLOSED">();
  readonly #ttlMs: number;
  readonly #maxSessions: number;
  readonly #now: () => number;
  readonly #idFactory: () => string;

  constructor(options: SessionManagerOptions = {}) {
    this.#ttlMs = options.ttlMs ?? 300_000;
    this.#maxSessions = options.maxSessions ?? 100;
    this.#now = options.now ?? Date.now;
    this.#idFactory = options.idFactory ?? randomUUID;
    if (!Number.isInteger(this.#ttlMs) || this.#ttlMs < 1_000) throw new Error("INVALID_SESSION_TTL");
    if (!Number.isInteger(this.#maxSessions) || this.#maxSessions < 1) throw new Error("INVALID_SESSION_LIMIT");
  }

  create(request: CreateSessionRequest) {
    this.sweep();
    if (this.#sessions.size >= this.#maxSessions) throw new ServiceError(503, "SESSION_CAPACITY_EXCEEDED", "Session capacity is exhausted", true);
    if (request.protocolVersion !== SIMULATION_PROTOCOL_VERSION) throw new ServiceError(400, "UNSUPPORTED_PROTOCOL", "Unsupported simulation protocol");
    if (!request.correlationId || request.correlationId.length > 128) throw new ServiceError(400, "INVALID_REQUEST", "A valid correlationId is required");
    const validated = validateGameConfig(request.gameConfig);
    if (!validated.valid) throw new ServiceError(400, "INVALID_GAME_CONFIG", "GameConfig validation failed");
    if (hashCanonical(validated.config) !== request.configDigest) throw new ServiceError(400, "CONFIG_DIGEST_MISMATCH", "GameConfig digest does not match");
    let core: SimulationCore;
    try {
      core = createSimulation(validated.config as GameConfig, request);
    } catch {
      throw new ServiceError(400, "INVALID_SIMULATION_INPUT", "Simulation input could not initialize the Core");
    }
    const sessionId = this.#idFactory();
    const expiresAt = this.#now() + this.#ttlMs;
    this.#sessions.set(sessionId, { core, episodeId: request.episodeId, expiresAt, busy: false });
    return { protocolVersion: SIMULATION_PROTOCOL_VERSION, sessionId, episodeId: request.episodeId, expiresAt, observation: core.observe() };
  }

  observe(sessionId: string): Observation {
    const record = this.#get(sessionId);
    if (record.busy) throw new ServiceError(409, "SESSION_BUSY", "Another operation is active for this session", true);
    this.#touch(record);
    return record.core.observe() as Observation;
  }

  async step(sessionId: string, action: Action): Promise<StepResult> {
    const record = this.#get(sessionId);
    if (record.busy) throw new ServiceError(409, "SESSION_BUSY", "Another operation is active for this session", true);
    record.busy = true;
    try {
      await new Promise<void>((resolve) => setImmediate(resolve));
      const result = record.core.step(action) as StepResult;
      this.#touch(record);
      if (result.error?.code === "EPISODE_TERMINATED") throw new ServiceError(409, "EPISODE_TERMINATED", "Episode is already terminated");
      return result;
    } finally {
      record.busy = false;
    }
  }

  close(sessionId: string): { protocolVersion: typeof SERVICE_PROTOCOL_VERSION; sessionId: string; closed: true } {
    const record = this.#sessions.get(sessionId);
    if (!record) {
      const gone = this.#gone.get(sessionId);
      if (gone === "SESSION_CLOSED") return { protocolVersion: SERVICE_PROTOCOL_VERSION, sessionId, closed: true };
      if (gone === "SESSION_EXPIRED") throw new ServiceError(410, gone, "Session has expired");
      throw new ServiceError(404, "SESSION_NOT_FOUND", "Session was not found");
    }
    if (record?.busy) throw new ServiceError(409, "SESSION_BUSY", "Another operation is active for this session", true);
    this.#sessions.delete(sessionId);
    this.#gone.set(sessionId, "SESSION_CLOSED");
    return { protocolVersion: SERVICE_PROTOCOL_VERSION, sessionId, closed: true };
  }

  sweep(): number {
    const now = this.#now();
    let removed = 0;
    for (const [sessionId, record] of this.#sessions) {
      if (!record.busy && record.expiresAt <= now) {
        this.#sessions.delete(sessionId);
        this.#gone.set(sessionId, "SESSION_EXPIRED");
        removed += 1;
      }
    }
    return removed;
  }

  clear(): void {
    this.#sessions.clear();
    this.#gone.clear();
  }

  get size(): number { return this.#sessions.size; }

  #get(sessionId: string): SessionRecord {
    this.sweep();
    const record = this.#sessions.get(sessionId);
    if (record) return record;
    const gone = this.#gone.get(sessionId);
    if (gone === "SESSION_EXPIRED") throw new ServiceError(410, gone, "Session has expired");
    if (gone === "SESSION_CLOSED") throw new ServiceError(410, gone, "Session has been closed");
    throw new ServiceError(404, "SESSION_NOT_FOUND", "Session was not found");
  }

  #touch(record: SessionRecord): void { record.expiresAt = this.#now() + this.#ttlMs; }
}
