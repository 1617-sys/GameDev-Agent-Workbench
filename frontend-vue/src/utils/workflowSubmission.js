export const GAME_GENERATE_WORKFLOW_KEY = "GAME_GENERATE";

export function createIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function prepareWorkflowSubmission(form, pendingSubmission, createKey = createIdempotencyKey) {
  const idea = form.idea.trim();
  const context = form.context.trim();
  if (!idea) return { validationError: "请输入游戏想法后再开始生成。" };
  if (idea.length > 5000) return { validationError: "游戏想法不能超过 5000 个字符。" };
  if (context.length > 5000) return { validationError: "补充上下文不能超过 5000 个字符。" };

  const request = { workflowKey: GAME_GENERATE_WORKFLOW_KEY, idea, context: context || null };
  const fingerprint = JSON.stringify(request);
  const nextPending = pendingSubmission?.fingerprint === fingerprint
    ? pendingSubmission
    : { fingerprint, idempotencyKey: createKey() };
  return { request, pendingSubmission: nextPending };
}
