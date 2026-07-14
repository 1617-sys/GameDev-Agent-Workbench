export function createIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `run-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function prepareSubmission({ idea, context }, pending = null) {
  const trimmedIdea = String(idea || "").trim();
  const trimmedContext = String(context || "").trim();
  if (!trimmedIdea) return { error: "请先描述你的游戏想法" };
  if (trimmedIdea.length > 5000 || trimmedContext.length > 5000) return { error: "输入内容不能超过 5000 个字符" };
  return {
    request: { workflowKey: "GAME_GENERATE", idea: trimmedIdea, context: trimmedContext },
    pending: pending || { idempotencyKey: createIdempotencyKey() }
  };
}
