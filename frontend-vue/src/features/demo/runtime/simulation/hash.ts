function rightRotate(value: number, amount: number): number {
  return (value >>> amount) | (value << (32 - amount));
}

export function canonicalStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonicalStringify).join(",")}]`;
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record).sort().map((key) => `${JSON.stringify(key)}:${canonicalStringify(record[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

export function sha256Hex(text: string): string {
  const maxWord = 2 ** 32;
  const words: number[] = [];
  const hash: number[] = [];
  const constants: number[] = [];
  const composite: Record<number, number> = {};
  let primeCounter = 0;

  for (let candidate = 2; primeCounter < 64; candidate += 1) {
    if (composite[candidate]) continue;
    for (let multiple = candidate; multiple < 313; multiple += candidate) composite[multiple] = candidate;
    hash[primeCounter] = (candidate ** 0.5 * maxWord) | 0;
    constants[primeCounter] = (candidate ** (1 / 3) * maxWord) | 0;
    primeCounter += 1;
  }

  const bytes = new TextEncoder().encode(text);
  const bitLength = bytes.length * 8;
  const data = [...bytes, 0x80];
  while ((data.length % 64) !== 56) data.push(0);
  for (let index = 7; index >= 0; index -= 1) data.push(index >= 4 ? 0 : (bitLength >>> (index * 8)) & 255);

  for (let offset = 0; offset < data.length; offset += 64) {
    for (let index = 0; index < 16; index += 1) {
      words[index] = (data[offset + index * 4] << 24)
        | (data[offset + index * 4 + 1] << 16)
        | (data[offset + index * 4 + 2] << 8)
        | data[offset + index * 4 + 3];
    }
    for (let index = 16; index < 64; index += 1) {
      const previous15 = words[index - 15];
      const previous2 = words[index - 2];
      words[index] = (words[index - 16]
        + (rightRotate(previous15, 7) ^ rightRotate(previous15, 18) ^ (previous15 >>> 3))
        + words[index - 7]
        + (rightRotate(previous2, 17) ^ rightRotate(previous2, 19) ^ (previous2 >>> 10))) | 0;
    }

    const original = hash.slice(0, 8);
    const working = original.slice();
    for (let index = 0; index < 64; index += 1) {
      const sigma1 = rightRotate(working[4], 6) ^ rightRotate(working[4], 11) ^ rightRotate(working[4], 25);
      const choice = (working[4] & working[5]) ^ (~working[4] & working[6]);
      const temporary1 = (working[7] + sigma1 + choice + constants[index] + words[index]) | 0;
      const sigma0 = rightRotate(working[0], 2) ^ rightRotate(working[0], 13) ^ rightRotate(working[0], 22);
      const majority = (working[0] & working[1]) ^ (working[0] & working[2]) ^ (working[1] & working[2]);
      const temporary2 = (sigma0 + majority) | 0;
      working.pop();
      working.unshift((temporary1 + temporary2) | 0);
      working[4] = (working[4] + temporary1) | 0;
    }
    for (let index = 0; index < 8; index += 1) hash[index] = (original[index] + working[index]) | 0;
  }

  return hash.slice(0, 8).map((value) => (value >>> 0).toString(16).padStart(8, "0")).join("");
}

export function hashCanonical(value: unknown): string {
  return sha256Hex(canonicalStringify(value));
}
