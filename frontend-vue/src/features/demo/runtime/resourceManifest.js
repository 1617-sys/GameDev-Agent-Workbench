const image = (category, url) => Object.freeze({ kind: "image", category, url });
const tone = (frequency, durationMs) => Object.freeze({ kind: "tone", category: "sound", frequency, durationMs });

export const RUNTIME_RESOURCE_MANIFEST = Object.freeze({
  "player.blue": image("player", "/runtime-assets/player.svg"),
  "player.green": image("player", "/runtime-assets/player.svg"),
  "collectible.gem": image("collectible", "/runtime-assets/collectible.svg"),
  "collectible.artifact": image("collectible", "/runtime-assets/collectible.svg"),
  "collectible.core": image("collectible", "/runtime-assets/collectible.svg"),
  "enemy.guard": image("enemy", "/runtime-assets/enemy.svg"),
  "enemy.drone": image("enemy", "/runtime-assets/enemy.svg"),
  "exit.portal": image("exit", "/runtime-assets/exit.svg"),
  "exit.door": image("exit", "/runtime-assets/exit.svg"),
  "obstacle.stone": image("obstacle", "/runtime-assets/obstacle.svg"),
  "obstacle.metal": image("obstacle", "/runtime-assets/obstacle.svg"),
  "obstacle.wood": image("obstacle", "/runtime-assets/obstacle.svg"),
  "sfx.collect": tone(740, 90),
  "sfx.hit": tone(150, 130),
  "sfx.win": tone(880, 240),
  "sfx.lose": tone(110, 300),
  "sfx.silent": tone(null, 0)
});

export const textureKeyFor = (resourceKey) => `runtime-${resourceKey.replaceAll(".", "-")}`;

export function configuredImageKeys(config) {
  return [...new Set([
    config.player.spriteKey,
    ...config.world.obstacles.map((entry) => entry.spriteKey),
    ...config.entities.collectibles.map((entry) => entry.spriteKey),
    ...config.entities.enemies.map((entry) => entry.spriteKey),
    config.entities.exit.spriteKey
  ])];
}
export function playManifestSound(resourceKey, onWarning = () => {}) {
  const descriptor = RUNTIME_RESOURCE_MANIFEST[resourceKey];
  if (!descriptor || descriptor.kind !== "tone" || descriptor.frequency == null) return;
  try {
    const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
    if (!AudioContext) throw new Error("Web Audio unavailable");
    const context = new AudioContext();
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.frequency.value = descriptor.frequency;
    gain.gain.setValueAtTime(0.045, context.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + descriptor.durationMs / 1000);
    oscillator.connect(gain).connect(context.destination);
    oscillator.start();
    oscillator.stop(context.currentTime + descriptor.durationMs / 1000);
    oscillator.addEventListener("ended", () => context.close(), { once: true });
  } catch {
    onWarning(`音效 ${resourceKey} 无法播放，已使用静音占位。`);
  }
}
