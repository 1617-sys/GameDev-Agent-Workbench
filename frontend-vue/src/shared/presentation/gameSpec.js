const profiles = Object.freeze({
  visualThemeId: "forest-01",
  assetPackId: "forest-adventure-01",
  animationProfileId: "topdown-character-01",
  cameraProfileId: "follow-soft-01",
  feedbackProfileId: "arcade-juice-01",
  uiSkinId: "forest-hud-01",
  audioProfileId: "forest-light-01"
});

const collectibleSpots = [[280, 145], [510, 375], [710, 130], [420, 245], [735, 405], [205, 405]];
const enemySpots = [[485, 275], [720, 275], [355, 405], [610, 145]];
const obstacleSpots = [[650, 205], [350, 300], [770, 350], [540, 120], [245, 250], [555, 400]];

const count = (value, fallback, max) => Math.max(0, Math.min(max, Number.isFinite(Number(value)) ? Math.trunc(Number(value)) : fallback));

export function defaultGameSpecForm() {
  return {
    title: "Forest Collector",
    description: "收集全部水晶，避开守卫并抵达传送门。",
    seed: 42,
    width: 960,
    height: 540,
    timeLimitSeconds: 90,
    backgroundColor: "#10251b",
    playerSpeed: 180,
    playerHealth: 3,
    playerRadius: 20,
    collectibleCount: 3,
    enemyCount: 1,
    obstacleCount: 1
  };
}

export function createArcadeCollectSpec(input = {}) {
  const form = { ...defaultGameSpecForm(), ...input };
  const width = Math.max(640, Math.min(1920, count(form.width, 960, 1920)));
  const height = Math.max(360, Math.min(1080, count(form.height, 540, 1080)));
  const scaleX = width / 960;
  const scaleY = height / 540;
  const point = ([x, y]) => ({ x: Math.round(x * scaleX), y: Math.round(y * scaleY) });
  const entities = [];
  collectibleSpots.slice(0, count(form.collectibleCount, 3, collectibleSpots.length)).forEach((spot, index) => {
    entities.push({ id: `crystal-${index + 1}`, type: "collectible", ...point(spot), size: 24, score: 100 });
  });
  enemySpots.slice(0, count(form.enemyCount, 1, enemySpots.length)).forEach((spot, index) => {
    entities.push({ id: `guardian-${index + 1}`, type: "enemy", ...point(spot), size: 36, speed: 80 + index * 10, patrolAxis: index % 2 ? "x" : "y", patrolRange: 120 + index * 15 });
  });
  obstacleSpots.slice(0, count(form.obstacleCount, 1, obstacleSpots.length)).forEach((spot, index) => {
    entities.push({ id: `stone-${index + 1}`, type: "obstacle", ...point(spot), size: 76 + index * 8 });
  });
  entities.push({ id: "forest-exit", type: "exit", x: width - 90, y: Math.round(height / 2), size: 72 });
  return {
    specVersion: "0.1",
    archetype: "arcade_collect",
    metadata: {
      title: String(form.title || "Forest Collector").trim(),
      seed: count(form.seed, 42, 2147483647),
      description: String(form.description || "").trim()
    },
    world: {
      width,
      height,
      timeLimitSeconds: Math.max(30, Math.min(600, count(form.timeLimitSeconds, 90, 600))),
      backgroundColor: /^#[0-9a-f]{6}$/i.test(form.backgroundColor || "") ? form.backgroundColor : "#10251b"
    },
    player: {
      movement: "four_way",
      speed: Math.max(80, Math.min(420, count(form.playerSpeed, 180, 420))),
      health: Math.max(1, Math.min(10, count(form.playerHealth, 3, 10))),
      radius: Math.max(12, Math.min(48, count(form.playerRadius, 20, 48))),
      spawn: { x: 90, y: Math.round(height / 2) }
    },
    entities,
    rules: [{
      when: "collectible.collected",
      if: { counter: "remainingCollectibles", equals: 0 },
      then: [{ action: "exit.unlock" }]
    }],
    presentation: { ...profiles }
  };
}

export function parsePersistedJson(value, fallback = []) {
  if (!value) return fallback;
  if (typeof value !== "string") return value;
  try { return JSON.parse(value); } catch { return fallback; }
}

export function generationStatusMeta(status = "") {
  return ({
    VALIDATING: { label: "校验中", tone: "info", step: 1 },
    BUILDING: { label: "等待构建", tone: "warning", step: 2 },
    PLAYTESTING: { label: "可试玩", tone: "success", step: 4 },
    AWAITING_APPROVAL: { label: "等待审批", tone: "warning", step: 4 },
    APPROVED: { label: "已批准", tone: "success", step: 4 },
    REJECTED: { label: "已拒绝", tone: "danger", step: 4 },
    FAILED: { label: "失败", tone: "danger", step: 2 },
    CANCELLED: { label: "已取消", tone: "neutral", step: 2 }
  })[status] || { label: status || "尚未创建", tone: "neutral", step: 0 };
}
