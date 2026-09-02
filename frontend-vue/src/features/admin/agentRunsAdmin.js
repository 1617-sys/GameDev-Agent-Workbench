const runFields = ["runUuid", "projectUuid", "agentType", "status", "errorMessage", "timeTakenMs", "provider", "modelName", "mockState", "traceId", "errorCategory", "createdAt", "updatedAt"];
const required = (value, label, max = Infinity) => { const text = String(value || "").trim(); if (!text) throw new Error(`${label}不能为空`); if (text.length > max) throw new Error(`${label}过长`); return text; };

export function safeAgentRun(source = {}) { return Object.fromEntries(runFields.map(field => [field, source[field] ?? null])); }
export function agentRunsQuery(filters = {}) {
  const query = new URLSearchParams({ pageNum: String(Math.max(1, Number(filters.pageNum) || 1)), pageSize: String(Math.min(100, Math.max(1, Number(filters.pageSize) || 20))) });
  for (const field of ["projectUuid", "agentType", "status"]) if (filters[field]) query.set(field, filters[field]);
  return query;
}
export function agentRunPayload(form, confirmed) {
  if (!confirmed) throw new Error("请先确认本次 Agent 运行可能产生模型成本");
  return { projectUuid: required(form.projectUuid, "项目 UUID"), agentType: required(form.agentType, "Agent 类型"), title: required(form.title, "标题", 200), content: required(form.content, "内容"), context: String(form.context || "").slice(0, 2000), ragEnabled: Boolean(form.ragEnabled), ragTopK: Math.min(20, Math.max(1, Number(form.ragTopK) || 5)), ragContextBudget: Math.min(50000, Math.max(1, Number(form.ragContextBudget) || 8000)) };
}
export function agentRunAttempt(payload, prior, keyFactory = () => crypto.randomUUID()) {
  const fingerprint = JSON.stringify(payload);
  return prior?.fingerprint === fingerprint ? prior : { key: keyFactory(), fingerprint };
}
