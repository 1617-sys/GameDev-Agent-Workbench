import { hashCanonical } from "./hash.ts";
import {
  SIMULATION_PROTOCOL_VERSION,
  TICK_MS,
  type Action,
  type ActionType,
  type CollectibleState,
  type EnemyState,
  type GameConfig,
  type LastActionObservation,
  type Observation,
  type ObservationPolicy,
  type ObstacleState,
  type SimulationOptions,
  type SimulationState,
  type StepError,
  type StepResult,
  type TelemetryEvent,
  type TerminationReason
} from "./types.ts";

const ACTION_TYPES = new Set<ActionType>(["MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT", "WAIT", "RESTART"]);
const DIGEST = /^[0-9a-f]{64}$/;
const TYPE_ORDER: Record<string, number> = { enemy: 0, collectible: 1, obstacle: 2, exit: 3 };

interface RuntimeRules {
  worldWidthMp: number;
  worldHeightMp: number;
  spawnXMp: number;
  spawnYMp: number;
  playerSpeedMpPerSecond: number;
  playerRadiusMp: number;
  maxHealth: number;
  hitInvulnerabilityMs: number;
  contactDamage: number;
  loseHealth: boolean;
  loseTime: boolean;
  timeLimitMs: number;
  winBonus: number;
  targetCollectibles: number;
  obstacles: ObstacleState[];
  collectibles: Array<Omit<CollectibleState, "active">>;
  enemies: Array<Omit<EnemyState, "direction" | "velocity">>;
  exit: SimulationState["exit"];
}

interface MoveResult {
  xMp: number;
  yMp: number;
  blocked: boolean;
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

function readonlyCopy<T>(value: T): Readonly<T> {
  return deepFreeze(jsonClone(value));
}

function safeRequestedAction(value: unknown): unknown {
  try {
    return jsonClone(value);
  } catch {
    return null;
  }
}

function toMp(value: number): number {
  const result = Math.round(value * 1000);
  if (!Number.isSafeInteger(result)) throw new Error("UNSAFE_COORDINATE");
  return result;
}

function toPx(value: number): number {
  return value / 1000;
}

function compareId(left: string, right: string): number {
  return left < right ? -1 : left > right ? 1 : 0;
}

function seededDirections(seed: number, count: number): Array<-1 | 1> {
  let value = seed >>> 0;
  const directions: Array<-1 | 1> = [];
  for (let index = 0; index < count; index += 1) {
    value = (value + 0x6d2b79f5) >>> 0;
    let mixed = value;
    mixed = Math.imul(mixed ^ (mixed >>> 15), mixed | 1);
    mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), mixed | 61);
    const random = ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
    directions.push(random < 0.5 ? -1 : 1);
  }
  return directions;
}

function circleCircle(left: { position: { xMp: number; yMp: number }; radiusMp: number }, right: { position: { xMp: number; yMp: number }; radiusMp: number }): boolean {
  const deltaX = left.position.xMp - right.position.xMp;
  const deltaY = left.position.yMp - right.position.yMp;
  const radius = left.radiusMp + right.radiusMp;
  return deltaX * deltaX + deltaY * deltaY <= radius * radius;
}

function circleRectangle(circle: { position: { xMp: number; yMp: number }; radiusMp: number }, rectangle: { center: { xMp: number; yMp: number }; widthMp: number; heightMp: number }): boolean {
  const minimumX = rectangle.center.xMp - rectangle.widthMp / 2;
  const maximumX = rectangle.center.xMp + rectangle.widthMp / 2;
  const minimumY = rectangle.center.yMp - rectangle.heightMp / 2;
  const maximumY = rectangle.center.yMp + rectangle.heightMp / 2;
  const closestX = Math.max(minimumX, Math.min(circle.position.xMp, maximumX));
  const closestY = Math.max(minimumY, Math.min(circle.position.yMp, maximumY));
  const deltaX = circle.position.xMp - closestX;
  const deltaY = circle.position.yMp - closestY;
  return deltaX * deltaX + deltaY * deltaY <= circle.radiusMp * circle.radiusMp;
}

function visibleRectangleDistanceSquared(player: SimulationState["player"], rectangle: { center: { xMp: number; yMp: number }; widthMp: number; heightMp: number }): number {
  const minimumX = rectangle.center.xMp - rectangle.widthMp / 2;
  const maximumX = rectangle.center.xMp + rectangle.widthMp / 2;
  const minimumY = rectangle.center.yMp - rectangle.heightMp / 2;
  const maximumY = rectangle.center.yMp + rectangle.heightMp / 2;
  const closestX = Math.max(minimumX, Math.min(player.position.xMp, maximumX));
  const closestY = Math.max(minimumY, Math.min(player.position.yMp, maximumY));
  return (player.position.xMp - closestX) ** 2 + (player.position.yMp - closestY) ** 2;
}

function createRules(config: GameConfig): RuntimeRules {
  const patrols = new Map(config.behaviors.enemyPatrols.map((entry) => [entry.enemyId, entry]));
  const obstacles = config.world.obstacles.map((entry) => ({
    id: entry.id,
    center: { xMp: toMp(entry.x), yMp: toMp(entry.y) },
    widthMp: toMp(entry.width),
    heightMp: toMp(entry.height)
  }));
  const collectibles = config.entities.collectibles.map((entry) => ({
    id: entry.id,
    position: { xMp: toMp(entry.x), yMp: toMp(entry.y) },
    radiusMp: toMp(entry.size / 2),
    score: entry.score
  }));
  const enemies = config.entities.enemies.map((entry) => {
    const patrol = patrols.get(entry.id);
    if (!patrol) throw new Error(`MISSING_PATROL:${entry.id}`);
    const xMp = toMp(entry.x);
    const yMp = toMp(entry.y);
    return {
      id: entry.id,
      position: { xMp, yMp },
      radiusMp: toMp(entry.size / 2),
      axis: patrol.axis,
      originMp: patrol.axis === "x" ? xMp : yMp,
      patrolDistanceMp: toMp(patrol.distance),
      speedMpPerSecond: toMp(entry.speed)
    };
  });
  return {
    worldWidthMp: toMp(config.world.width),
    worldHeightMp: toMp(config.world.height),
    spawnXMp: toMp(config.world.spawn.x),
    spawnYMp: toMp(config.world.spawn.y),
    playerSpeedMpPerSecond: toMp(config.player.speed),
    playerRadiusMp: toMp(config.player.size / 2),
    maxHealth: config.player.maxHealth,
    hitInvulnerabilityMs: config.player.hitInvulnerabilityMs,
    contactDamage: config.behaviors.contact.damage,
    loseHealth: config.objectives.loseConditions.includes("health_depleted"),
    loseTime: config.objectives.loseConditions.includes("time_expired"),
    timeLimitMs: config.balance.timeLimitSeconds * 1000,
    winBonus: config.balance.winBonus,
    targetCollectibles: config.objectives.targetCollectibles,
    obstacles,
    collectibles,
    enemies,
    exit: {
      id: "exit",
      center: { xMp: toMp(config.entities.exit.x), yMp: toMp(config.entities.exit.y) },
      widthMp: toMp(config.entities.exit.width),
      heightMp: toMp(config.entities.exit.height),
      unlocked: false
    }
  };
}

function validateOptions(options: SimulationOptions): void {
  if (options.protocolVersion !== SIMULATION_PROTOCOL_VERSION) throw new Error("UNSUPPORTED_PROTOCOL");
  if (typeof options.episodeId !== "string" || !options.episodeId.trim()) throw new Error("INVALID_EPISODE_ID");
  if (!DIGEST.test(options.configDigest)) throw new Error("INVALID_CONFIG_DIGEST");
  if (!Number.isInteger(options.seed) || options.seed < 0 || options.seed > 0xffffffff) throw new Error("INVALID_SEED");
  if (!Number.isInteger(options.maxSteps) || options.maxSteps < 1 || options.maxSteps > 1_000_000) throw new Error("INVALID_MAX_STEPS");
  const policy = options.observationPolicy;
  if (!policy || !["FULL", "PERSONA"].includes(policy.kind)) throw new Error("INVALID_OBSERVATION_POLICY");
  if (policy.kind === "PERSONA" && (!Number.isInteger(policy.visionRadiusPx) || policy.visionRadiusPx < 1 || policy.visionRadiusPx > 2000)) {
    throw new Error("INVALID_VISION_RADIUS");
  }
}

export class SimulationCore {
  readonly #rules: RuntimeRules;
  readonly #options: SimulationOptions;
  #state: SimulationState;
  #lastAction: LastActionObservation;

  constructor(config: GameConfig, options: SimulationOptions) {
    validateOptions(options);
    this.#rules = createRules(jsonClone(config));
    this.#options = jsonClone(options);
    this.#state = this.#initialState(1, 0, 0);
    this.#lastAction = { type: null, accepted: true, code: null, scoreDelta: 0, events: ["SESSION_STARTED"] };
    this.#assertState();
  }

  snapshot(): Readonly<SimulationState> {
    return readonlyCopy(this.#state);
  }

  stateHash(): string {
    return hashCanonical(this.#state);
  }

  observe(): Readonly<Observation> {
    return readonlyCopy(this.#observation(this.stateHash(), this.#lastAction));
  }

  step(requestedAction: unknown): Readonly<StepResult> {
    const previousState = jsonClone(this.#state);
    const previousStateHash = hashCanonical(previousState);
    const requested = safeRequestedAction(requestedAction);
    const action = this.#normalizeAction(requestedAction);

    if (this.#state.status === "TERMINATED") {
      return this.#rejected(requested, previousStateHash, "EPISODE_TERMINATED", "Episode is already terminated");
    }
    if (!action) {
      return this.#rejected(requested, previousStateHash, "INVALID_ACTION", "Action must be a closed object with one supported type");
    }

    try {
      const beforeScore = this.#state.score;
      const events: TelemetryEvent[] = [];
      let advanced = false;

      if (action.type === "RESTART") this.#restart(events);
      else {
        advanced = true;
        this.#advance(action, events);
      }

      this.beforeFinalize();
      this.#assertState();
      const stateHash = this.stateHash();
      const scoreDelta = this.#state.score - beforeScore;
      this.#lastAction = { type: action.type, accepted: true, code: null, scoreDelta, events: events.map((event) => event.type) };
      return readonlyCopy(this.#result(requested, action, true, advanced, previousStateHash, stateHash, scoreDelta, events, null));
    } catch (cause) {
      this.#state = previousState;
      this.#state.step += 1;
      this.#state.attemptStep += 1;
      this.#terminate("ERROR");
      const stateHash = this.stateHash();
      const error: StepError = { code: "INTERNAL_ERROR", message: "Simulation invariant failed", retriable: false };
      this.#lastAction = { type: action.type, accepted: true, code: error.code, scoreDelta: 0, events: [] };
      return readonlyCopy(this.#result(requested, action, true, false, previousStateHash, stateHash, 0, [], error));
    }
  }

  protected beforeFinalize(): void {}

  #initialState(attempt: number, step: number, restartCount: number): SimulationState {
    const directions = seededDirections(this.#options.seed, this.#rules.enemies.length);
    const enemies = this.#rules.enemies.map((entry, index) => {
      const direction = directions[index];
      return {
        ...jsonClone(entry),
        direction,
        velocity: entry.axis === "x"
          ? { xMpPerSecond: entry.speedMpPerSecond * direction, yMpPerSecond: 0 }
          : { xMpPerSecond: 0, yMpPerSecond: entry.speedMpPerSecond * direction }
      } as EnemyState;
    });
    return {
      protocolVersion: SIMULATION_PROTOCOL_VERSION,
      episodeId: this.#options.episodeId,
      configDigest: this.#options.configDigest,
      seed: this.#options.seed,
      tickMs: TICK_MS,
      step,
      maxSteps: this.#options.maxSteps,
      attempt,
      attemptStep: 0,
      elapsedMs: 0,
      remainingMs: this.#rules.timeLimitMs,
      status: "RUNNING",
      terminationReason: null,
      restartCount,
      score: 0,
      collectedIds: [],
      targetCollectibles: this.#rules.targetCollectibles,
      exitUnlocked: false,
      player: {
        id: "player",
        position: { xMp: this.#rules.spawnXMp, yMp: this.#rules.spawnYMp },
        velocity: { xMpPerSecond: 0, yMpPerSecond: 0 },
        radiusMp: this.#rules.playerRadiusMp,
        health: this.#rules.maxHealth,
        maxHealth: this.#rules.maxHealth,
        invulnerableUntilMs: 0
      },
      enemies,
      obstacles: jsonClone(this.#rules.obstacles),
      collectibles: this.#rules.collectibles.map((entry) => ({ ...jsonClone(entry), active: true })),
      exit: jsonClone(this.#rules.exit)
    };
  }

  #normalizeAction(value: unknown): Action | null {
    if (!value || typeof value !== "object" || Array.isArray(value)) return null;
    const keys = Object.keys(value);
    if (keys.length !== 1 || keys[0] !== "type") return null;
    const type = (value as { type?: unknown }).type;
    return typeof type === "string" && ACTION_TYPES.has(type as ActionType) ? { type: type as ActionType } : null;
  }

  #restart(events: TelemetryEvent[]): void {
    const nextStep = this.#state.step + 1;
    const nextAttempt = this.#state.attempt + 1;
    const restartCount = this.#state.restartCount + 1;
    this.#state = this.#initialState(nextAttempt, nextStep, restartCount);
    events.push({ type: "SESSION_RESTARTED", payload: {} });
    if (this.#state.step >= this.#state.maxSteps) this.#terminate("MAX_STEPS");
  }

  #advance(action: Action, events: TelemetryEvent[]): void {
    this.#state.step += 1;
    this.#state.attemptStep += 1;
    this.#state.elapsedMs += TICK_MS;
    this.#state.remainingMs = Math.max(0, this.#state.remainingMs - TICK_MS);
    if (this.#state.remainingMs === 0 && this.#rules.loseTime) {
      events.push({ type: "GAME_LOST", payload: { reason: "TIME_EXPIRED" } });
      this.#terminate("TIME_EXPIRED");
      return;
    }

    this.#movePlayer(action);
    this.#moveEnemies();
    this.#collect(events);
    this.#damage(events);
    if (this.#state.status === "TERMINATED") return;
    this.#win(events);
    if (this.#state.status === "RUNNING" && this.#state.step >= this.#state.maxSteps) this.#terminate("MAX_STEPS");
  }

  #movePlayer(action: Action): void {
    const velocity = { xMpPerSecond: 0, yMpPerSecond: 0 };
    if (action.type === "MOVE_UP") velocity.yMpPerSecond = -this.#rules.playerSpeedMpPerSecond;
    if (action.type === "MOVE_DOWN") velocity.yMpPerSecond = this.#rules.playerSpeedMpPerSecond;
    if (action.type === "MOVE_LEFT") velocity.xMpPerSecond = -this.#rules.playerSpeedMpPerSecond;
    if (action.type === "MOVE_RIGHT") velocity.xMpPerSecond = this.#rules.playerSpeedMpPerSecond;
    this.#state.player.velocity = velocity;
    const move = this.#sweep(
      this.#state.player.position.xMp,
      this.#state.player.position.yMp,
      this.#state.player.radiusMp,
      velocity.xMpPerSecond * TICK_MS / 1000,
      velocity.yMpPerSecond * TICK_MS / 1000
    );
    this.#state.player.position = { xMp: move.xMp, yMp: move.yMp };
  }

  #moveEnemies(): void {
    for (const enemy of this.#state.enemies) {
      const displacement = enemy.speedMpPerSecond * TICK_MS / 1000 * enemy.direction;
      const deltaX = enemy.axis === "x" ? displacement : 0;
      const deltaY = enemy.axis === "y" ? displacement : 0;
      const patrolMinimum = enemy.originMp - enemy.patrolDistanceMp;
      const patrolMaximum = enemy.originMp + enemy.patrolDistanceMp;
      const move = this.#sweep(enemy.position.xMp, enemy.position.yMp, enemy.radiusMp, deltaX, deltaY, enemy.axis, patrolMinimum, patrolMaximum);
      enemy.position = { xMp: move.xMp, yMp: move.yMp };
      if (move.blocked) enemy.direction = enemy.direction === 1 ? -1 : 1;
      enemy.velocity = enemy.axis === "x"
        ? { xMpPerSecond: enemy.speedMpPerSecond * enemy.direction, yMpPerSecond: 0 }
        : { xMpPerSecond: 0, yMpPerSecond: enemy.speedMpPerSecond * enemy.direction };
    }
  }

  #sweep(xMp: number, yMp: number, radiusMp: number, deltaX: number, deltaY: number, patrolAxis?: "x" | "y", patrolMinimum?: number, patrolMaximum?: number): MoveResult {
    const horizontal = deltaX !== 0;
    const displacement = horizontal ? deltaX : deltaY;
    if (displacement === 0) return { xMp, yMp, blocked: false };
    const direction = displacement > 0 ? 1 : -1;
    const requestedDistance = Math.abs(displacement);
    const coordinate = horizontal ? xMp : yMp;
    const worldMinimum = radiusMp;
    const worldMaximum = (horizontal ? this.#rules.worldWidthMp : this.#rules.worldHeightMp) - radiusMp;
    let availableDistance = direction > 0 ? worldMaximum - coordinate : coordinate - worldMinimum;

    if (patrolAxis === (horizontal ? "x" : "y")) {
      const patrolDistance = direction > 0 ? (patrolMaximum as number) - coordinate : coordinate - (patrolMinimum as number);
      availableDistance = Math.min(availableDistance, patrolDistance);
    }

    for (const obstacle of [...this.#state.obstacles].sort((left, right) => compareId(left.id, right.id))) {
      const minimumX = obstacle.center.xMp - obstacle.widthMp / 2 - radiusMp;
      const maximumX = obstacle.center.xMp + obstacle.widthMp / 2 + radiusMp;
      const minimumY = obstacle.center.yMp - obstacle.heightMp / 2 - radiusMp;
      const maximumY = obstacle.center.yMp + obstacle.heightMp / 2 + radiusMp;
      let distance: number | null = null;
      if (horizontal && yMp >= minimumY && yMp <= maximumY) {
        if (direction > 0 && xMp <= minimumX) distance = minimumX - xMp;
        if (direction < 0 && xMp >= maximumX) distance = xMp - maximumX;
      } else if (!horizontal && xMp >= minimumX && xMp <= maximumX) {
        if (direction > 0 && yMp <= minimumY) distance = minimumY - yMp;
        if (direction < 0 && yMp >= maximumY) distance = yMp - maximumY;
      }
      if (distance !== null && distance >= 0 && distance < availableDistance) availableDistance = distance;
    }

    availableDistance = Math.max(0, availableDistance);
    const actualDistance = Math.min(requestedDistance, availableDistance);
    return {
      xMp: horizontal ? xMp + direction * actualDistance : xMp,
      yMp: horizontal ? yMp : yMp + direction * actualDistance,
      blocked: availableDistance <= requestedDistance
    };
  }

  #collect(events: TelemetryEvent[]): void {
    const contacts = this.#state.collectibles
      .filter((entry) => entry.active && circleCircle(this.#state.player, entry))
      .sort((left, right) => compareId(left.id, right.id));
    for (const collectible of contacts) {
      collectible.active = false;
      this.#state.collectedIds.push(collectible.id);
      this.#state.score += collectible.score;
      events.push({ type: "ITEM_COLLECTED", payload: { itemId: collectible.id } });
    }
    this.#state.exitUnlocked = this.#state.collectedIds.length >= this.#state.targetCollectibles;
    this.#state.exit.unlocked = this.#state.exitUnlocked;
  }

  #damage(events: TelemetryEvent[]): void {
    if (this.#state.elapsedMs < this.#state.player.invulnerableUntilMs) return;
    const enemy = this.#state.enemies
      .filter((entry) => circleCircle(this.#state.player, entry))
      .sort((left, right) => compareId(left.id, right.id))[0];
    if (!enemy) return;
    const minimumHealth = this.#rules.loseHealth ? 0 : 1;
    this.#state.player.health = Math.max(minimumHealth, this.#state.player.health - this.#rules.contactDamage);
    this.#state.player.invulnerableUntilMs = this.#state.elapsedMs + this.#rules.hitInvulnerabilityMs;
    events.push({ type: "PLAYER_HIT", payload: { enemyId: enemy.id } });
    if (this.#state.player.health === 0 && this.#rules.loseHealth) {
      events.push({ type: "GAME_LOST", payload: { reason: "HEALTH_DEPLETED" } });
      this.#terminate("HEALTH_DEPLETED");
    }
  }

  #win(events: TelemetryEvent[]): void {
    if (!this.#state.exitUnlocked || !circleRectangle(this.#state.player, this.#state.exit)) return;
    this.#state.score += this.#rules.winBonus;
    events.push({ type: "GAME_WON", payload: {} });
    this.#terminate("WON");
  }

  #terminate(reason: TerminationReason): void {
    this.#state.status = "TERMINATED";
    this.#state.terminationReason = reason;
    this.#state.player.velocity = { xMpPerSecond: 0, yMpPerSecond: 0 };
    for (const enemy of this.#state.enemies) enemy.velocity = { xMpPerSecond: 0, yMpPerSecond: 0 };
  }

  #assertState(): void {
    const visit = (value: unknown): void => {
      if (typeof value === "number" && !Number.isSafeInteger(value)) throw new Error("UNSAFE_INTEGER_STATE");
      if (Array.isArray(value)) value.forEach(visit);
      else if (value && typeof value === "object") Object.values(value as Record<string, unknown>).forEach(visit);
    };
    visit(this.#state);
    if (this.#state.step < 0 || this.#state.step > this.#state.maxSteps) throw new Error("STEP_BUDGET_INVARIANT");
    if (this.#state.remainingMs < 0) throw new Error("TIME_INVARIANT");
    if (this.#state.player.health < 0 || this.#state.player.health > this.#state.player.maxHealth) throw new Error("HEALTH_INVARIANT");
    const unique = new Set(this.#state.collectedIds);
    if (unique.size !== this.#state.collectedIds.length) throw new Error("COLLECTION_DUPLICATE");
    const inactive = this.#state.collectibles.filter((entry) => !entry.active).map((entry) => entry.id);
    if (inactive.length !== unique.size || inactive.some((id) => !unique.has(id))) throw new Error("COLLECTION_INVARIANT");
    const expectedUnlocked = this.#state.collectedIds.length >= this.#state.targetCollectibles;
    if (this.#state.exitUnlocked !== expectedUnlocked || this.#state.exit.unlocked !== expectedUnlocked) throw new Error("EXIT_INVARIANT");
    if ((this.#state.status === "TERMINATED") !== (this.#state.terminationReason !== null)) throw new Error("TERMINATION_INVARIANT");
  }

  #rejected(requestedAction: unknown, stateHash: string, code: StepError["code"], message: string): Readonly<StepResult> {
    const error: StepError = { code, message, retriable: false };
    const type = requestedAction && typeof requestedAction === "object" && "type" in requestedAction ? String((requestedAction as { type: unknown }).type) : null;
    const lastAction = { type, accepted: false, code, scoreDelta: 0, events: [] };
    this.#lastAction = lastAction;
    return readonlyCopy(this.#result(requestedAction, null, false, false, stateHash, stateHash, 0, [], error, lastAction));
  }

  #result(requestedAction: unknown, appliedAction: Action | null, accepted: boolean, advanced: boolean, previousStateHash: string, stateHash: string, scoreDelta: number, events: TelemetryEvent[], error: StepError | null, lastAction = this.#lastAction): StepResult {
    return {
      protocolVersion: SIMULATION_PROTOCOL_VERSION,
      episodeId: this.#state.episodeId,
      step: this.#state.step,
      requestedAction,
      appliedAction,
      accepted,
      advanced,
      previousStateHash,
      stateHash,
      status: this.#state.status,
      terminationReason: this.#state.terminationReason,
      scoreDelta,
      events,
      error,
      observation: this.#observation(stateHash, lastAction)
    };
  }

  #observation(stateHash: string, lastAction: LastActionObservation): Observation {
    const player = this.#state.player;
    const policy = this.#options.observationPolicy;
    const radiusMp = policy.kind === "PERSONA" ? policy.visionRadiusPx * 1000 : Number.MAX_SAFE_INTEGER;
    const radiusSquared = radiusMp * radiusMp;
    const entities: Array<Record<string, unknown>> = [];
    for (const enemy of this.#state.enemies) {
      const deltaX = enemy.position.xMp - player.position.xMp;
      const deltaY = enemy.position.yMp - player.position.yMp;
      if (deltaX * deltaX + deltaY * deltaY > radiusSquared) continue;
      entities.push({ type: "enemy", id: enemy.id, position: { x: toPx(enemy.position.xMp), y: toPx(enemy.position.yMp) }, relative: { dx: toPx(deltaX), dy: toPx(deltaY) }, radius: toPx(enemy.radiusMp), velocity: { x: toPx(enemy.velocity.xMpPerSecond), y: toPx(enemy.velocity.yMpPerSecond) } });
    }
    for (const collectible of this.#state.collectibles) {
      if (!collectible.active) continue;
      const deltaX = collectible.position.xMp - player.position.xMp;
      const deltaY = collectible.position.yMp - player.position.yMp;
      if (deltaX * deltaX + deltaY * deltaY > radiusSquared) continue;
      entities.push({ type: "collectible", id: collectible.id, position: { x: toPx(collectible.position.xMp), y: toPx(collectible.position.yMp) }, relative: { dx: toPx(deltaX), dy: toPx(deltaY) }, radius: toPx(collectible.radiusMp) });
    }
    for (const obstacle of this.#state.obstacles) {
      if (visibleRectangleDistanceSquared(player, obstacle) > radiusSquared) continue;
      entities.push({ type: "obstacle", id: obstacle.id, position: { x: toPx(obstacle.center.xMp), y: toPx(obstacle.center.yMp) }, relative: { dx: toPx(obstacle.center.xMp - player.position.xMp), dy: toPx(obstacle.center.yMp - player.position.yMp) }, width: toPx(obstacle.widthMp), height: toPx(obstacle.heightMp) });
    }
    if (visibleRectangleDistanceSquared(player, this.#state.exit) <= radiusSquared) {
      entities.push({ type: "exit", id: "exit", position: { x: toPx(this.#state.exit.center.xMp), y: toPx(this.#state.exit.center.yMp) }, relative: { dx: toPx(this.#state.exit.center.xMp - player.position.xMp), dy: toPx(this.#state.exit.center.yMp - player.position.yMp) }, width: toPx(this.#state.exit.widthMp), height: toPx(this.#state.exit.heightMp), unlocked: this.#state.exit.unlocked });
    }
    entities.sort((left, right) => (TYPE_ORDER[String(left.type)] - TYPE_ORDER[String(right.type)]) || compareId(String(left.id), String(right.id)));
    return {
      protocolVersion: SIMULATION_PROTOCOL_VERSION,
      episodeId: this.#state.episodeId,
      kind: policy.kind,
      ...(policy.kind === "PERSONA" ? { visionRadiusPx: policy.visionRadiusPx } : {}),
      step: this.#state.step,
      attempt: this.#state.attempt,
      elapsedMs: this.#state.elapsedMs,
      remainingMs: this.#state.remainingMs,
      stateHash,
      status: this.#state.status,
      terminationReason: this.#state.terminationReason,
      player: {
        position: { x: toPx(player.position.xMp), y: toPx(player.position.yMp) },
        velocity: { x: toPx(player.velocity.xMpPerSecond), y: toPx(player.velocity.yMpPerSecond) },
        health: player.health,
        maxHealth: player.maxHealth,
        invulnerable: this.#state.elapsedMs < player.invulnerableUntilMs
      },
      progress: {
        collected: this.#state.collectedIds.length,
        target: this.#state.targetCollectibles,
        score: this.#state.score,
        exitUnlocked: this.#state.exitUnlocked,
        restartCount: this.#state.restartCount
      },
      visibleEntities: entities,
      lastAction: jsonClone(lastAction)
    };
  }
}

export function createSimulation(config: GameConfig, options: SimulationOptions): SimulationCore {
  return new SimulationCore(config, options);
}
