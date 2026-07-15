export const RUNTIME_STATES = Object.freeze({
  READY: "READY",
  PLAYING: "PLAYING",
  PAUSED: "PAUSED",
  WON: "WON",
  LOST: "LOST"
});

export function createSeededRandom(seed) {
  let value = Number(seed) >>> 0;
  return () => {
    value = (value + 0x6d2b79f5) >>> 0;
    let mixed = value;
    mixed = Math.imul(mixed ^ (mixed >>> 15), mixed | 1);
    mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), mixed | 61);
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
  };
}
export function deterministicEnemyDirections(config) {
  const random = createSeededRandom(config.metadata.seed);
  return Object.fromEntries(config.entities.enemies.map((enemy) => [enemy.id, random() < 0.5 ? -1 : 1]));
}

export class ArcadeCollectStateMachine {
  constructor(config, onChange = () => {}) {
    this.config = config;
    this.onChange = onChange;
    this.restartCount = 0;
    this.reset(false);
  }

  reset(isRestart = true) {
    if (isRestart) this.restartCount += 1;
    this.state = {
      status: RUNTIME_STATES.READY,
      health: this.config.player.maxHealth,
      score: 0,
      collectedIds: [],
      collected: 0,
      total: this.config.objectives.targetCollectibles,
      remainingMs: this.config.balance.timeLimitSeconds * 1000,
      elapsedMs: 0,
      invulnerableUntilMs: 0,
      exitUnlocked: false,
      outcomeReason: null,
      restartCount: this.restartCount,
      enemyDirections: deterministicEnemyDirections(this.config)
    };
    this.emit();
    return this.snapshot();
  }

  snapshot() {
    return { ...this.state, collectedIds: [...this.state.collectedIds], enemyDirections: { ...this.state.enemyDirections } };
  }

  start() {
    if (this.state.status !== RUNTIME_STATES.READY) return false;
    this.state.status = RUNTIME_STATES.PLAYING;
    this.emit();
    return true;
  }

  pause() {
    if (this.state.status !== RUNTIME_STATES.PLAYING) return false;
    this.state.status = RUNTIME_STATES.PAUSED;
    this.emit();
    return true;
  }

  resume() {
    if (this.state.status !== RUNTIME_STATES.PAUSED) return false;
    this.state.status = RUNTIME_STATES.PLAYING;
    this.emit();
    return true;
  }

  tick(deltaMs) {
    if (this.state.status !== RUNTIME_STATES.PLAYING) return false;
    const delta = Math.max(0, Number(deltaMs) || 0);
    this.state.elapsedMs += delta;
    this.state.remainingMs = Math.max(0, this.state.remainingMs - delta);
    if (this.state.remainingMs === 0 && this.config.objectives.loseConditions.includes("time_expired")) {
      this.finish(RUNTIME_STATES.LOST, "TIME_EXPIRED");
    }
    return true;
  }

  collect(itemId) {
    if (this.state.status !== RUNTIME_STATES.PLAYING || this.state.collectedIds.includes(itemId)) return false;
    const item = this.config.entities.collectibles.find((entry) => entry.id === itemId);
    if (!item) return false;
    this.state.collectedIds.push(itemId);
    this.state.collected = this.state.collectedIds.length;
    this.state.score += item.score;
    this.state.exitUnlocked = this.state.collected >= this.state.total;
    this.emit();
    return true;
  }

  hit() {
    if (this.state.status !== RUNTIME_STATES.PLAYING || this.state.elapsedMs < this.state.invulnerableUntilMs) return false;
    const canLoseHealth = this.config.objectives.loseConditions.includes("health_depleted");
    const minimumHealth = canLoseHealth ? 0 : 1;
    this.state.health = Math.max(minimumHealth, this.state.health - this.config.behaviors.contact.damage);
    this.state.invulnerableUntilMs = this.state.elapsedMs + this.config.player.hitInvulnerabilityMs;
    if (this.state.health === 0 && canLoseHealth) this.finish(RUNTIME_STATES.LOST, "HEALTH_DEPLETED");
    else this.emit();
    return true;
  }

  reachExit() {
    if (this.state.status !== RUNTIME_STATES.PLAYING || !this.state.exitUnlocked) return false;
    this.state.score += this.config.balance.winBonus;
    this.finish(RUNTIME_STATES.WON, "COMPLETED");
    return true;
  }

  finish(status, reason) {
    if (this.state.status !== RUNTIME_STATES.PLAYING || ![RUNTIME_STATES.WON, RUNTIME_STATES.LOST].includes(status)) return false;
    this.state.status = status;
    this.state.outcomeReason = reason;
    this.emit();
    return true;
  }

  emit() {
    this.onChange(this.snapshot());
  }
}
