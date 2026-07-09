export const defaultGameConfig = {
  version: "1.0",
  gameType: "top_down_collect",
  title: "Pixel Dungeon Escape",
  theme: "Collect gems, avoid enemies, and reach the exit.",
  world: {
    width: 960,
    height: 540,
    backgroundColor: "#111827"
  },
  player: {
    x: 96,
    y: 96,
    speed: 220,
    color: "#60a5fa"
  },
  collectibles: [
    { id: "gem-1", x: 260, y: 140, label: "Gem" },
    { id: "gem-2", x: 520, y: 300, label: "Gem" },
    { id: "gem-3", x: 760, y: 180, label: "Gem" }
  ],
  enemies: [
    { id: "enemy-1", x: 420, y: 220, speed: 90, patrolAxis: "x", patrolDistance: 180 },
    { id: "enemy-2", x: 700, y: 380, speed: 80, patrolAxis: "y", patrolDistance: 140 }
  ],
  exit: {
    x: 860,
    y: 450,
    lockedUntilCollected: true
  },
  winCondition: {
    collectAll: true,
    reachExit: true
  },
  ui: {
    objective: "Collect all gems, then reach the exit.",
    controlHint: "Move with WASD or arrow keys."
  }
};

