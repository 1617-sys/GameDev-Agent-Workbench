export function createIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `run-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function prepareSubmission({ idea, durationSeconds, difficulty, visualTheme, additionalRequirements }, pending = null) {
  const trimmedIdea = String(idea || "").trim();
  const duration = Number(durationSeconds);
  const normalizedDifficulty = String(difficulty || "").trim();
  const trimmedVisualTheme = String(visualTheme || "").trim();
  const trimmedAdditional = String(additionalRequirements || "").trim();
  if (!trimmedIdea) return { error: "请先描述你的游戏想法" };
  if (trimmedIdea.length > 5000) return { error: "主题内容不能超过 5000 个字符" };
  if (!Number.isInteger(duration) || duration < 30 || duration > 600) return { error: "游戏时长必须在 30 到 600 秒之间" };
  if (!["easy", "normal", "hard"].includes(normalizedDifficulty)) return { error: "请选择游戏难度" };
  if (!trimmedVisualTheme || trimmedVisualTheme.length > 80) return { error: "请填写不超过 80 个字符的视觉主题" };
  if (trimmedAdditional.length > 2000) return { error: "补充要求不能超过 2000 个字符" };
  return {
    request: {
      workflowKey: "GAME_GENERATE",
      idea: trimmedIdea,
      durationSeconds: duration,
      difficulty: normalizedDifficulty,
      visualTheme: trimmedVisualTheme,
      additionalRequirements: trimmedAdditional
    },
    pending: pending || { idempotencyKey: createIdempotencyKey() }
  };
}
