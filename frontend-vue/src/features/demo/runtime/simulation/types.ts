export const SIMULATION_PROTOCOL_VERSION = "simulation/1.0" as const;
export const TICK_MS = 50 as const;

export type ActionType = "MOVE_UP" | "MOVE_DOWN" | "MOVE_LEFT" | "MOVE_RIGHT" | "WAIT" | "RESTART";
export type TerminationReason = "WON" | "HEALTH_DEPLETED" | "TIME_EXPIRED" | "MAX_STEPS" | "ERROR";
export type SimulationStatus = "RUNNING" | "TERMINATED";

export interface Action {
  type: ActionType;
}

export interface FullObservationPolicy {
  kind: "FULL";
}

export interface PersonaObservationPolicy {
  kind: "PERSONA";
  visionRadiusPx: number;
}

export type ObservationPolicy = FullObservationPolicy | PersonaObservationPolicy;

export interface SimulationOptions {
  protocolVersion: typeof SIMULATION_PROTOCOL_VERSION;
  episodeId: string;
  configDigest: string;
  seed: number;
  maxSteps: number;
  observationPolicy: ObservationPolicy;
}

export interface GameConfig {
  metadata: { seed: number };
  world: {
    width: number;
    height: number;
    spawn: { x: number; y: number };
    obstacles: Array<{ id: string; x: number; y: number; width: number; height: number }>;
  };
  player: { speed: number; size: number; maxHealth: number; hitInvulnerabilityMs: number };
  entities: {
    collectibles: Array<{ id: string; x: number; y: number; size: number; score: number }>;
    enemies: Array<{ id: string; x: number; y: number; size: number; speed: number }>;
    exit: { x: number; y: number; width: number; height: number };
  };
  behaviors: {
    enemyPatrols: Array<{ enemyId: string; axis: "x" | "y"; distance: number }>;
    contact: { damage: number };
  };
  objectives: {
    targetCollectibles: number;
    loseConditions: Array<"health_depleted" | "time_expired">;
  };
  balance: { timeLimitSeconds: number; winBonus: number };
}

export interface PointState {
  xMp: number;
  yMp: number;
}

export interface VelocityState {
  xMpPerSecond: number;
  yMpPerSecond: number;
}

export interface PlayerState {
  id: "player";
  position: PointState;
  velocity: VelocityState;
  radiusMp: number;
  health: number;
  maxHealth: number;
  invulnerableUntilMs: number;
}

export interface EnemyState {
  id: string;
  position: PointState;
  velocity: VelocityState;
  radiusMp: number;
  axis: "x" | "y";
  originMp: number;
  patrolDistanceMp: number;
  direction: -1 | 1;
  speedMpPerSecond: number;
}

export interface ObstacleState {
  id: string;
  center: PointState;
  widthMp: number;
  heightMp: number;
}

export interface CollectibleState {
  id: string;
  position: PointState;
  radiusMp: number;
  score: number;
  active: boolean;
}

export interface ExitState {
  id: "exit";
  center: PointState;
  widthMp: number;
  heightMp: number;
  unlocked: boolean;
}

export interface SimulationState {
  protocolVersion: typeof SIMULATION_PROTOCOL_VERSION;
  episodeId: string;
  configDigest: string;
  seed: number;
  tickMs: typeof TICK_MS;
  step: number;
  maxSteps: number;
  attempt: number;
  attemptStep: number;
  elapsedMs: number;
  remainingMs: number;
  status: SimulationStatus;
  terminationReason: TerminationReason | null;
  restartCount: number;
  score: number;
  collectedIds: string[];
  targetCollectibles: number;
  exitUnlocked: boolean;
  player: PlayerState;
  enemies: EnemyState[];
  obstacles: ObstacleState[];
  collectibles: CollectibleState[];
  exit: ExitState;
}

export interface TelemetryEvent {
  type: "SESSION_STARTED" | "ITEM_COLLECTED" | "PLAYER_HIT" | "GAME_WON" | "GAME_LOST" | "SESSION_RESTARTED" | "SESSION_ENDED";
  payload: Record<string, unknown>;
}

export interface LastActionObservation {
  type: string | null;
  accepted: boolean;
  code: string | null;
  scoreDelta: number;
  events: string[];
}

export interface Observation {
  protocolVersion: typeof SIMULATION_PROTOCOL_VERSION;
  episodeId: string;
  kind: ObservationPolicy["kind"];
  visionRadiusPx?: number;
  step: number;
  attempt: number;
  elapsedMs: number;
  remainingMs: number;
  stateHash: string;
  status: SimulationStatus;
  terminationReason: TerminationReason | null;
  player: Record<string, unknown>;
  progress: Record<string, unknown>;
  visibleEntities: Array<Record<string, unknown>>;
  lastAction: LastActionObservation;
}

export interface StepError {
  code: "INVALID_ACTION" | "EPISODE_TERMINATED" | "INTERNAL_ERROR";
  message: string;
  retriable: false;
}

export interface StepResult {
  protocolVersion: typeof SIMULATION_PROTOCOL_VERSION;
  episodeId: string;
  step: number;
  requestedAction: unknown;
  appliedAction: Action | null;
  accepted: boolean;
  advanced: boolean;
  previousStateHash: string;
  stateHash: string;
  status: SimulationStatus;
  terminationReason: TerminationReason | null;
  scoreDelta: number;
  events: TelemetryEvent[];
  error: StepError | null;
  observation: Observation;
}
