import { validateGameConfig } from "../gameConfig.js";
import {
  SIMULATION_PROTOCOL_VERSION,
  createSimulation,
  hashCanonical,
  type ObservationPolicy,
  type StepResult,
  type TerminationReason
} from "../simulation/index.ts";

export const EPISODE_PROTOCOL_VERSION = "episode/1.0" as const;
export const HEADLESS_LIMITS = Object.freeze({
  maxBatchSize: 100,
  maxConcurrency: 16,
  maxSteps: 1_000_000,
  maxDecisions: 1_000_000,
  maxEpisodeTimeoutMs: 120_000,
  maxBatchTimeoutMs: 300_000
});

type JsonRecord = Record<string, unknown>;
type ExecutionStatus = "COMPLETED" | "FAILED" | "REJECTED" | "CANCELLED";

export interface HeadlessEpisodeRequest {
  episodeProtocolVersion: typeof EPISODE_PROTOCOL_VERSION;
  clientEpisodeKey: string;
  prototype: JsonRecord;
  simulation: {
    protocolVersion: typeof SIMULATION_PROTOCOL_VERSION;
    coreVersion: string;
    seed: number;
    maxSteps: number;
    observationPolicy: ObservationPolicy;
  };
  policy: JsonRecord & { kind: "DETERMINISTIC" | "LLM" };
  persona: JsonRecord;
  model: JsonRecord | null;
  metricVersion: string;
  experiment: JsonRecord | null;
  labels: Record<string, string>;
}

export interface HeadlessEpisodeInvocation {
  episodeId: string;
  batchId: string;
  request: HeadlessEpisodeRequest;
  gameConfig: unknown;
  actionSequence: unknown[];
  actionMode?: "CYCLE" | "REPEAT_LAST";
  timeoutMs: number;
  maxDecisions?: number;
}

export interface HeadlessBatchInvocation {
  episodeProtocolVersion: typeof EPISODE_PROTOCOL_VERSION;
  batchId: string;
  clientBatchKey: string;
  concurrency: number;
  timeoutMs: number;
  episodes: HeadlessEpisodeInvocation[];
}

export interface RunnerClock {
  now(): number;
  yieldControl(): Promise<void>;
}

export class HeadlessRunnerValidationError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "HeadlessRunnerValidationError";
    this.code = code;
  }
}

const defaultClock: RunnerClock = {
  now: () => performance.now(),
  yieldControl: () => Promise.resolve()
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SAFE_KEY = /^[A-Za-z0-9._:@/-]{1,80}$/;
const DIGEST = /^[0-9a-f]{64}$/;

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function finiteJson(value: unknown, seen = new Set<object>()): boolean {
  if (value === null || typeof value === "string" || typeof value === "boolean") return true;
  if (typeof value === "number") return Number.isFinite(value);
  if (!value || typeof value !== "object" || seen.has(value)) return false;
  seen.add(value);
  const valid = Array.isArray(value)
    ? value.every((entry) => finiteJson(entry, seen))
    : Object.values(value).every((entry) => finiteJson(entry, seen));
  seen.delete(value);
  return valid;
}

function error(phase: string, code: string, message: string, failedSequence: number | null = null) {
  return { phase, code, message, retryable: false, failedSequence };
}

function usage() {
  return {
    status: "NOT_APPLICABLE",
    inputTokens: null,
    outputTokens: null,
    totalTokens: null,
    costMicros: null,
    currency: null,
    providerLatencyMs: null,
    unavailableReason: null
  };
}

function outcome(reason: TerminationReason | null): "WON" | "LOST" | "TRUNCATED" | "ERROR" | null {
  if (reason === "WON") return "WON";
  if (reason === "HEALTH_DEPLETED" || reason === "TIME_EXPIRED") return "LOST";
  if (reason === "MAX_STEPS") return "TRUNCATED";
  if (reason === "ERROR") return "ERROR";
  return null;
}

function baseResult(invocation: HeadlessEpisodeInvocation, executionStatus: ExecutionStatus) {
  const request = invocation.request;
  return {
    episodeProtocolVersion: EPISODE_PROTOCOL_VERSION,
    episodeId: invocation.episodeId,
    batchId: invocation.batchId,
    clientEpisodeKey: request?.clientEpisodeKey ?? "invalid",
    sampleSource: "MACHINE",
    prototype: clone(request?.prototype ?? {}),
    simulation: clone(request?.simulation ?? {}),
    policy: clone(request?.policy ?? {}),
    persona: clone(request?.persona ?? {}),
    model: clone(request?.model ?? null),
    metricVersion: request?.metricVersion ?? "score-delta/1.0",
    executionStatus,
    terminationReason: null,
    outcome: null,
    stepCount: 0,
    acceptedActionCount: 0,
    invalidActionCount: 0,
    finalStateHash: null,
    finalScore: null,
    trajectoryDigest: null,
    steps: [] as JsonRecord[],
    usage: usage(),
    timing: { queuedMs: 0, wallDurationMs: null, simulationDurationMs: null, policyDurationMs: null },
    error: null as ReturnType<typeof error> | null,
    audit: {
      actorType: "SERVICE",
      actorId: "node-headless-runner",
      traceId: `headless-${invocation.episodeId}`,
      resultRef: `episodes/${invocation.episodeId}/result`
    }
  };
}

function rejected(invocation: HeadlessEpisodeInvocation, code: string, message: string) {
  const result = baseResult(invocation, "REJECTED");
  result.error = error("VALIDATION", code, message);
  return result;
}

function validateInvocation(invocation: HeadlessEpisodeInvocation) {
  if (!finiteJson(invocation)) return { error: "Invocation must be finite, acyclic JSON" };
  if (!UUID.test(invocation.episodeId) || !UUID.test(invocation.batchId)) return { error: "episodeId and batchId must be UUIDs" };
  const request = invocation.request;
  if (!request || request.episodeProtocolVersion !== EPISODE_PROTOCOL_VERSION) return { error: "Unsupported Episode protocol" };
  if (!SAFE_KEY.test(request.clientEpisodeKey)) return { error: "Invalid clientEpisodeKey" };
  if (request.policy?.kind !== "DETERMINISTIC" || request.model !== null) return { error: "Headless v1 requires a deterministic policy and null model" };
  if (request.metricVersion !== "score-delta/1.0") return { error: "Unsupported metricVersion" };
  if (request.simulation?.protocolVersion !== SIMULATION_PROTOCOL_VERSION) return { error: "Unsupported Simulation protocol" };
  if (!request.simulation.coreVersion || request.simulation.coreVersion === "latest") return { error: "coreVersion must be immutable" };
  if (!Number.isInteger(request.simulation.seed) || request.simulation.seed < 0 || request.simulation.seed > 0xffffffff) return { error: "Invalid seed" };
  if (!Number.isInteger(request.simulation.maxSteps) || request.simulation.maxSteps < 1 || request.simulation.maxSteps > HEADLESS_LIMITS.maxSteps) return { error: "Invalid maxSteps" };
  if (!Array.isArray(invocation.actionSequence) || invocation.actionSequence.length < 1) return { error: "actionSequence must not be empty" };
  if (!Number.isInteger(invocation.timeoutMs) || invocation.timeoutMs < 1 || invocation.timeoutMs > HEADLESS_LIMITS.maxEpisodeTimeoutMs) return { error: "Invalid episode timeout" };
  const maxDecisions = invocation.maxDecisions ?? request.simulation.maxSteps;
  if (!Number.isInteger(maxDecisions) || maxDecisions < 1 || maxDecisions > HEADLESS_LIMITS.maxDecisions) return { error: "Invalid maxDecisions" };
  const validated = validateGameConfig(invocation.gameConfig);
  if (!validated.valid) return { error: "Invalid GameConfig" };
  const digest = hashCanonical(validated.config);
  if (!DIGEST.test(String(request.prototype?.configDigest ?? "")) || request.prototype.configDigest !== digest) return { error: "GameConfig digest mismatch" };
  return { config: validated.config, maxDecisions };
}

function transition(result: Readonly<StepResult>) {
  return {
    appliedAction: result.appliedAction,
    accepted: result.accepted,
    advanced: result.advanced,
    previousStateHash: result.previousStateHash,
    stateHash: result.stateHash,
    scoreDelta: result.scoreDelta,
    events: result.events,
    status: result.status,
    terminationReason: result.terminationReason,
    error: result.error
  };
}

export async function runHeadlessEpisode(invocation: HeadlessEpisodeInvocation, clock: RunnerClock = defaultClock, absoluteDeadline = Number.POSITIVE_INFINITY) {
  const validation = validateInvocation(invocation);
  if (validation.error) return rejected(invocation, validation.error === "Invalid GameConfig" ? "INVALID_GAME_CONFIG" : "INVALID_EPISODE_REQUEST", validation.error);

  const startedAt = clock.now();
  const deadline = Math.min(startedAt + invocation.timeoutMs, absoluteDeadline);
  const request = invocation.request;
  const options = {
    protocolVersion: SIMULATION_PROTOCOL_VERSION,
    episodeId: invocation.episodeId,
    configDigest: request.prototype.configDigest as string,
    seed: request.simulation.seed,
    maxSteps: request.simulation.maxSteps,
    observationPolicy: request.simulation.observationPolicy
  };
  let simulation;
  const simulationStartedAt = clock.now();
  try {
    simulation = createSimulation(validation.config, options);
  } catch {
    return rejected(invocation, "INVALID_SIMULATION_INPUT", "Simulation input could not initialize the Core");
  }
  let simulationDurationMs = Math.max(0, Math.floor(clock.now() - simulationStartedAt));

  const result = baseResult(invocation, "COMPLETED");
  result.finalStateHash = simulation.stateHash();
  result.finalScore = 0;
  let policyDurationMs = 0;
  let failure: ReturnType<typeof error> | null = null;
  const actionMode = invocation.actionMode ?? "CYCLE";

  for (let sequence = 1; simulation.snapshot().status === "RUNNING"; sequence += 1) {
    if (clock.now() >= deadline) {
      failure = error("RUNNER", "EPISODE_TIMEOUT", "Episode exceeded its configured deadline", sequence);
      break;
    }
    if (sequence > validation.maxDecisions) {
      failure = error("RUNNER", "DECISION_LIMIT_EXCEEDED", "Episode exceeded its decision budget", sequence);
      break;
    }

    const before = simulation.snapshot();
    const observation = simulation.observe();
    const policyStartedAt = clock.now();
    const actionIndex = actionMode === "REPEAT_LAST"
      ? Math.min(sequence - 1, invocation.actionSequence.length - 1)
      : (sequence - 1) % invocation.actionSequence.length;
    const requestedAction = clone(invocation.actionSequence[actionIndex]);
    const duration = Math.max(0, Math.floor(clock.now() - policyStartedAt));
    policyDurationMs += duration;
    const stepStartedAt = clock.now();
    const stepResult = simulation.step(requestedAction);
    simulationDurationMs += Math.max(0, Math.floor(clock.now() - stepStartedAt));
    result.steps.push({
      sequence,
      attempt: before.attempt,
      simulationStepBefore: before.step,
      simulationStepAfter: stepResult.step,
      observation,
      observationDigest: hashCanonical(observation),
      decision: { requestedAction: stepResult.requestedAction, policyDurationMs: duration, modelCallId: null },
      transition: transition(stepResult),
      reward: { version: request.metricVersion, valueMicros: stepResult.accepted ? stepResult.scoreDelta * 1_000_000 : 0 }
    });
    await clock.yieldControl();
  }

  const state = simulation.snapshot();
  result.stepCount = result.steps.length;
  result.acceptedActionCount = result.steps.filter((step) => (step.transition as JsonRecord).accepted === true).length;
  result.invalidActionCount = result.stepCount - result.acceptedActionCount;
  result.finalStateHash = simulation.stateHash();
  result.finalScore = state.score;
  result.trajectoryDigest = hashCanonical(result.steps);
  result.terminationReason = state.terminationReason;
  result.outcome = outcome(state.terminationReason);
  result.timing = {
    queuedMs: 0,
    wallDurationMs: Math.max(0, Math.floor(clock.now() - startedAt)),
    simulationDurationMs,
    policyDurationMs
  };
  if (failure) {
    result.executionStatus = "FAILED";
    result.outcome = "ERROR";
    result.error = failure;
  } else if (state.terminationReason === "ERROR") {
    result.executionStatus = "FAILED";
    result.error = error("SIMULATION", "CORE_ERROR", "Simulation Core terminated with an internal error", result.stepCount);
  }
  return clone(result);
}

function validateBatch(invocation: HeadlessBatchInvocation) {
  if (!finiteJson(invocation)) throw new HeadlessRunnerValidationError("INVALID_BATCH", "Batch invocation must be finite, acyclic JSON");
  if (invocation.episodeProtocolVersion !== EPISODE_PROTOCOL_VERSION) throw new HeadlessRunnerValidationError("UNSUPPORTED_EPISODE_PROTOCOL", "Unsupported Episode protocol");
  if (!UUID.test(invocation.batchId) || !SAFE_KEY.test(invocation.clientBatchKey)) throw new HeadlessRunnerValidationError("INVALID_BATCH", "Invalid batchId or clientBatchKey");
  if (!Array.isArray(invocation.episodes) || invocation.episodes.length < 1 || invocation.episodes.length > HEADLESS_LIMITS.maxBatchSize) throw new HeadlessRunnerValidationError("INVALID_BATCH_SIZE", "Batch size must be 1..100");
  if (!Number.isInteger(invocation.concurrency) || invocation.concurrency < 1 || invocation.concurrency > HEADLESS_LIMITS.maxConcurrency) throw new HeadlessRunnerValidationError("INVALID_CONCURRENCY", "Concurrency is outside the supported range");
  if (!Number.isInteger(invocation.timeoutMs) || invocation.timeoutMs < 1 || invocation.timeoutMs > HEADLESS_LIMITS.maxBatchTimeoutMs) throw new HeadlessRunnerValidationError("INVALID_BATCH_TIMEOUT", "Batch timeout is outside the supported range");
  const keys = invocation.episodes.map((entry) => entry.request?.clientEpisodeKey);
  if (new Set(keys).size !== keys.length) throw new HeadlessRunnerValidationError("DUPLICATE_CLIENT_EPISODE_KEY", "clientEpisodeKey must be unique within a batch");
  if (invocation.episodes.some((entry) => entry.batchId !== invocation.batchId)) throw new HeadlessRunnerValidationError("BATCH_ID_MISMATCH", "Every episode must reference the enclosing batchId");
}

export async function runHeadlessBatch(invocation: HeadlessBatchInvocation, clock: RunnerClock = defaultClock) {
  validateBatch(invocation);
  const startedAt = clock.now();
  const deadline = startedAt + invocation.timeoutMs;
  const results = new Array(invocation.episodes.length);
  let nextIndex = 0;
  const worker = async () => {
    while (nextIndex < invocation.episodes.length) {
      const index = nextIndex;
      nextIndex += 1;
      const episode = clone(invocation.episodes[index]);
      const remainingMs = Math.floor(invocation.timeoutMs - (clock.now() - startedAt));
      episode.timeoutMs = Math.max(1, Math.min(episode.timeoutMs, remainingMs));
      results[index] = await runHeadlessEpisode(episode, clock, deadline);
    }
  };
  await Promise.all(Array.from({ length: Math.min(invocation.concurrency, invocation.episodes.length) }, worker));

  const counts = { total: results.length, queued: 0, running: 0, completed: 0, failed: 0, rejected: 0, cancelled: 0 };
  const terminationReasons: Record<string, number> = {};
  for (const result of results) {
    const key = String(result.executionStatus).toLowerCase() as "completed" | "failed" | "rejected" | "cancelled";
    counts[key] += 1;
    const reason = result.terminationReason ?? "NONE";
    terminationReasons[reason] = (terminationReasons[reason] ?? 0) + 1;
  }
  const status = counts.completed === counts.total ? "SUCCEEDED" : counts.completed > 0 ? "PARTIAL_SUCCESS" : "FAILED";
  const requestFingerprint = hashCanonical({
    episodeProtocolVersion: invocation.episodeProtocolVersion,
    clientBatchKey: invocation.clientBatchKey,
    episodes: invocation.episodes.map((entry) => entry.request)
  });
  const items = results.map((result) => ({
    clientEpisodeKey: result.clientEpisodeKey,
    episodeId: result.episodeId,
    executionStatus: result.executionStatus,
    resultRef: result.audit.resultRef,
    error: result.error
  }));
  return clone({
    episodeProtocolVersion: EPISODE_PROTOCOL_VERSION,
    batchId: invocation.batchId,
    clientBatchKey: invocation.clientBatchKey,
    requestFingerprint,
    status,
    counts,
    items,
    results,
    statistics: { executionStatuses: { completed: counts.completed, failed: counts.failed, rejected: counts.rejected, cancelled: counts.cancelled }, terminationReasons },
    timing: { wallDurationMs: Math.max(0, Math.floor(clock.now() - startedAt)) }
  });
}

export const runEpisode = runHeadlessEpisode;
export const runEpisodeBatch = runHeadlessBatch;
