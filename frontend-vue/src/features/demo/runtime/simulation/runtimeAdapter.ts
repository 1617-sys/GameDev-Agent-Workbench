import { hashCanonical } from "./hash.ts";
import { recordActionSequence } from "./replay.ts";
import { createSimulation, type SimulationCore } from "./simulationCore.ts";
import {
  SIMULATION_PROTOCOL_VERSION,
  TICK_MS,
  type Action,
  type GameConfig,
  type SimulationOptions,
  type SimulationState,
  type StepResult
} from "./types.ts";

export type RuntimeHostStatus = "READY" | "PLAYING" | "PAUSED" | "WON" | "LOST";

export interface RuntimeHudState {
  status: RuntimeHostStatus;
  health: number;
  score: number;
  collectedIds: string[];
  collected: number;
  total: number;
  elapsedMs: number;
  remainingMs: number;
  exitUnlocked: boolean;
  outcomeReason: SimulationState["terminationReason"];
  restartCount: number;
  stateHash: string;
}

export interface RuntimeRestartResult {
  recreated: boolean;
  elapsedMs: number;
  result: Readonly<StepResult> | null;
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

export function createRuntimeSimulationOptions(config: GameConfig, episodeSequence = 1): SimulationOptions {
  const configDigest = hashCanonical(config);
  return {
    protocolVersion: SIMULATION_PROTOCOL_VERSION,
    episodeId: `phaser-${config.metadata.seed}-${configDigest.slice(0, 16)}-${episodeSequence}`,
    configDigest,
    seed: config.metadata.seed,
    maxSteps: 1_000_000,
    observationPolicy: { kind: "FULL" }
  };
}

export class RuntimeSimulationAdapter {
  readonly #config: GameConfig;
  #episodeSequence = 1;
  #options: SimulationOptions;
  #simulation: SimulationCore;
  #actions: Action[] = [];
  #accumulatorMs = 0;
  #started = false;
  #paused = false;

  constructor(config: GameConfig) {
    this.#config = clone(config);
    this.#options = createRuntimeSimulationOptions(this.#config, this.#episodeSequence);
    this.#simulation = createSimulation(this.#config, this.#options);
  }

  start(): boolean {
    if (this.#started || this.#simulation.snapshot().status !== "RUNNING") return false;
    this.#started = true;
    this.#paused = false;
    return true;
  }

  togglePause(): boolean {
    if (!this.#started || this.#simulation.snapshot().status !== "RUNNING") return false;
    this.#paused = !this.#paused;
    return true;
  }

  advance(deltaMs: number, action: Action): Readonly<StepResult>[] {
    if (!this.#started || this.#paused || this.#simulation.snapshot().status !== "RUNNING") return [];
    if (!Number.isFinite(deltaMs) || deltaMs <= 0) return [];
    this.#accumulatorMs += deltaMs;
    const results: Readonly<StepResult>[] = [];
    while (this.#accumulatorMs >= TICK_MS && this.#simulation.snapshot().status === "RUNNING") {
      const requestedAction = clone(action);
      const result = this.#simulation.step(requestedAction);
      this.#actions.push(requestedAction);
      results.push(result);
      this.#accumulatorMs -= TICK_MS;
    }
    return results;
  }

  restart(): RuntimeRestartResult {
    const elapsedMs = this.#simulation.snapshot().elapsedMs;
    if (this.#simulation.snapshot().status === "RUNNING") {
      const action: Action = { type: "RESTART" };
      const result = this.#simulation.step(action);
      this.#actions.push(action);
      this.#accumulatorMs = 0;
      this.#started = true;
      this.#paused = false;
      return { recreated: false, elapsedMs, result };
    }

    this.#episodeSequence += 1;
    this.#options = createRuntimeSimulationOptions(this.#config, this.#episodeSequence);
    this.#simulation = createSimulation(this.#config, this.#options);
    this.#actions = [];
    this.#accumulatorMs = 0;
    this.#started = true;
    this.#paused = false;
    return { recreated: true, elapsedMs, result: null };
  }

  snapshot(): Readonly<SimulationState> {
    return this.#simulation.snapshot();
  }

  stateHash(): string {
    return this.#simulation.stateHash();
  }

  status(): RuntimeHostStatus {
    const state = this.#simulation.snapshot();
    if (!this.#started) return "READY";
    if (state.status === "TERMINATED") return state.terminationReason === "WON" ? "WON" : "LOST";
    if (this.#paused) return "PAUSED";
    return "PLAYING";
  }

  hudState(): RuntimeHudState {
    const state = this.#simulation.snapshot();
    return {
      status: this.status(),
      health: state.player.health,
      score: state.score,
      collectedIds: [...state.collectedIds],
      collected: state.collectedIds.length,
      total: state.targetCollectibles,
      elapsedMs: state.elapsedMs,
      remainingMs: state.remainingMs,
      exitUnlocked: state.exitUnlocked,
      outcomeReason: state.terminationReason,
      restartCount: state.restartCount,
      stateHash: this.#simulation.stateHash()
    };
  }

  replayTrace() {
    return recordActionSequence(this.#config, this.#options, this.#actions);
  }

  simulationOptions(): SimulationOptions {
    return clone(this.#options);
  }
}
