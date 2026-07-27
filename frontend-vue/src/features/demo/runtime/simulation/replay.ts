import { hashCanonical } from "./hash.ts";
import { RANDOM_ALGORITHM_VERSION } from "./prng.ts";
import { createSimulation } from "./simulationCore.ts";
import {
  SIMULATION_PROTOCOL_VERSION,
  type GameConfig,
  type ObservationPolicy,
  type SimulationOptions,
  type TerminationReason
} from "./types.ts";

export interface ReplayStep {
  sequence: number;
  requestedAction: unknown;
  actionDigest: string;
  expectedAccepted: boolean;
  expectedStateHash: string;
  expectedTerminationReason: TerminationReason | null;
}

export interface ReplayTrace {
  protocolVersion: typeof SIMULATION_PROTOCOL_VERSION;
  randomAlgorithm: typeof RANDOM_ALGORITHM_VERSION;
  episodeId: string;
  configDigest: string;
  seed: number;
  maxSteps: number;
  observationPolicy: ObservationPolicy;
  observationPolicyDigest: string;
  initialStateHash: string;
  steps: ReplayStep[];
  finalStateHash: string;
  terminationReason: TerminationReason | null;
}

export type ReplayErrorCode =
  | "REPLAY_PROTOCOL_MISMATCH"
  | "REPLAY_RANDOM_ALGORITHM_MISMATCH"
  | "REPLAY_EPISODE_ID_MISMATCH"
  | "REPLAY_CONFIG_DIGEST_MISMATCH"
  | "REPLAY_SEED_MISMATCH"
  | "REPLAY_MAX_STEPS_MISMATCH"
  | "REPLAY_OBSERVATION_POLICY_MISMATCH"
  | "REPLAY_TRACE_INVALID"
  | "REPLAY_INITIAL_STATE_MISMATCH"
  | "REPLAY_ACTION_DIGEST_MISMATCH"
  | "REPLAY_ACTION_COMPATIBILITY_MISMATCH"
  | "REPLAY_STATE_HASH_MISMATCH"
  | "REPLAY_TERMINATION_MISMATCH"
  | "REPLAY_FINAL_STATE_MISMATCH"
  | "REPLAY_INPUT_INVALID";

export interface ReplayError {
  code: ReplayErrorCode;
  sequence: number;
  message: string;
  expected: unknown;
  actual: unknown;
}

export interface ReplayResult {
  ok: boolean;
  comparedSteps: number;
  finalStateHash: string | null;
  terminationReason: TerminationReason | null;
  error: ReplayError | null;
}

function jsonClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function deepFreeze<T>(value: T): Readonly<T> {
  if (!value || typeof value !== "object" || Object.isFrozen(value)) return value as Readonly<T>;
  Object.freeze(value);
  for (const child of Object.values(value as Record<string, unknown>)) deepFreeze(child);
  return value as Readonly<T>;
}

function immutable<T>(value: T): Readonly<T> {
  return deepFreeze(jsonClone(value));
}

function failure(code: ReplayErrorCode, sequence: number, message: string, expected: unknown, actual: unknown, comparedSteps = 0, finalStateHash: string | null = null, terminationReason: TerminationReason | null = null): Readonly<ReplayResult> {
  return immutable({
    ok: false,
    comparedSteps,
    finalStateHash,
    terminationReason,
    error: { code, sequence, message, expected, actual }
  });
}

export function recordActionSequence(config: GameConfig, options: SimulationOptions, actions: readonly unknown[]): Readonly<ReplayTrace> {
  const simulation = createSimulation(config, options);
  const initialStateHash = simulation.stateHash();
  const steps: ReplayStep[] = [];
  for (const [index, requestedAction] of actions.entries()) {
    const result = simulation.step(requestedAction);
    steps.push({
      sequence: index + 1,
      requestedAction: jsonClone(result.requestedAction),
      actionDigest: hashCanonical(result.requestedAction),
      expectedAccepted: result.accepted,
      expectedStateHash: result.stateHash,
      expectedTerminationReason: result.terminationReason
    });
  }
  const state = simulation.snapshot();
  return immutable({
    protocolVersion: SIMULATION_PROTOCOL_VERSION,
    randomAlgorithm: RANDOM_ALGORITHM_VERSION,
    episodeId: options.episodeId,
    configDigest: options.configDigest,
    seed: options.seed,
    maxSteps: options.maxSteps,
    observationPolicy: jsonClone(options.observationPolicy),
    observationPolicyDigest: hashCanonical(options.observationPolicy),
    initialStateHash,
    steps,
    finalStateHash: simulation.stateHash(),
    terminationReason: state.terminationReason
  });
}

export function replayActionSequence(config: GameConfig, options: SimulationOptions, trace: ReplayTrace): Readonly<ReplayResult> {
  if (!trace || typeof trace !== "object") return failure("REPLAY_TRACE_INVALID", 0, "Replay trace must be an object", "ReplayTrace", trace);
  if (trace.protocolVersion !== SIMULATION_PROTOCOL_VERSION || trace.protocolVersion !== options.protocolVersion) {
    return failure("REPLAY_PROTOCOL_MISMATCH", 0, "Simulation protocol versions do not match", trace.protocolVersion, options.protocolVersion);
  }
  if (trace.randomAlgorithm !== RANDOM_ALGORITHM_VERSION) {
    return failure("REPLAY_RANDOM_ALGORITHM_MISMATCH", 0, "Seeded random algorithm versions do not match", trace.randomAlgorithm, RANDOM_ALGORITHM_VERSION);
  }
  if (trace.episodeId !== options.episodeId) {
    return failure("REPLAY_EPISODE_ID_MISMATCH", 0, "Episode IDs do not match", trace.episodeId, options.episodeId);
  }
  if (trace.configDigest !== options.configDigest) {
    return failure("REPLAY_CONFIG_DIGEST_MISMATCH", 0, "GameConfig digests do not match", trace.configDigest, options.configDigest);
  }
  if (trace.seed !== options.seed) return failure("REPLAY_SEED_MISMATCH", 0, "Seeds do not match", trace.seed, options.seed);
  if (trace.maxSteps !== options.maxSteps) return failure("REPLAY_MAX_STEPS_MISMATCH", 0, "Step budgets do not match", trace.maxSteps, options.maxSteps);
  const policyDigest = hashCanonical(options.observationPolicy);
  if (trace.observationPolicyDigest !== policyDigest) {
    return failure("REPLAY_OBSERVATION_POLICY_MISMATCH", 0, "Observation policies do not match", trace.observationPolicyDigest, policyDigest);
  }
  if (!Array.isArray(trace.steps)) return failure("REPLAY_TRACE_INVALID", 0, "Replay steps must be an array", "array", typeof trace.steps);

  let simulation;
  try {
    simulation = createSimulation(config, options);
  } catch {
    return failure("REPLAY_INPUT_INVALID", 0, "Replay input could not initialize the Simulation Core", "valid input", "invalid input");
  }
  const initialStateHash = simulation.stateHash();
  if (initialStateHash !== trace.initialStateHash) {
    return failure("REPLAY_INITIAL_STATE_MISMATCH", 0, "Initial state hash differs", trace.initialStateHash, initialStateHash, 0, initialStateHash, simulation.snapshot().terminationReason);
  }

  for (const [index, step] of trace.steps.entries()) {
    const sequence = index + 1;
    if (!step || step.sequence !== sequence || typeof step.actionDigest !== "string" || typeof step.expectedStateHash !== "string") {
      return failure("REPLAY_TRACE_INVALID", sequence, "Replay step sequence or required fields are invalid", sequence, step?.sequence, index, simulation.stateHash(), simulation.snapshot().terminationReason);
    }
    const actionDigest = hashCanonical(step.requestedAction);
    if (actionDigest !== step.actionDigest) {
      return failure("REPLAY_ACTION_DIGEST_MISMATCH", sequence, "Recorded action was modified", step.actionDigest, actionDigest, index, simulation.stateHash(), simulation.snapshot().terminationReason);
    }
    const result = simulation.step(step.requestedAction);
    if (result.accepted !== step.expectedAccepted) {
      return failure("REPLAY_ACTION_COMPATIBILITY_MISMATCH", sequence, "Action acceptance changed", step.expectedAccepted, result.accepted, sequence, result.stateHash, result.terminationReason);
    }
    if (result.stateHash !== step.expectedStateHash) {
      return failure("REPLAY_STATE_HASH_MISMATCH", sequence, "State hash diverged", step.expectedStateHash, result.stateHash, sequence, result.stateHash, result.terminationReason);
    }
    if (result.terminationReason !== step.expectedTerminationReason) {
      return failure("REPLAY_TERMINATION_MISMATCH", sequence, "Termination reason diverged", step.expectedTerminationReason, result.terminationReason, sequence, result.stateHash, result.terminationReason);
    }
  }

  const finalStateHash = simulation.stateHash();
  if (finalStateHash !== trace.finalStateHash) {
    return failure("REPLAY_FINAL_STATE_MISMATCH", trace.steps.length, "Final state hash diverged", trace.finalStateHash, finalStateHash, trace.steps.length, finalStateHash, simulation.snapshot().terminationReason);
  }
  return immutable({
    ok: true,
    comparedSteps: trace.steps.length,
    finalStateHash,
    terminationReason: simulation.snapshot().terminationReason,
    error: null
  });
}
