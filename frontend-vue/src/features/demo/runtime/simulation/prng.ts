export const RANDOM_ALGORITHM_VERSION = "mulberry32/1.0" as const;

export class SeededRandom {
  readonly algorithm = RANDOM_ALGORITHM_VERSION;
  readonly seed: number;
  #value: number;

  constructor(seed: number) {
    if (!Number.isInteger(seed) || seed < 0 || seed > 0xffffffff) throw new Error("INVALID_SEED");
    this.seed = seed >>> 0;
    this.#value = this.seed;
  }

  next(): number {
    this.#value = (this.#value + 0x6d2b79f5) >>> 0;
    let mixed = this.#value;
    mixed = Math.imul(mixed ^ (mixed >>> 15), mixed | 1);
    mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), mixed | 61);
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
  }
}

export function seededEnemyDirections(seed: number, count: number): Array<-1 | 1> {
  if (!Number.isInteger(count) || count < 0) throw new Error("INVALID_RANDOM_COUNT");
  const random = new SeededRandom(seed);
  return Array.from({ length: count }, () => random.next() < 0.5 ? -1 : 1);
}
