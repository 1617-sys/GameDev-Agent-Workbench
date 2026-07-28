const TERMINAL = new Set(["SUCCEEDED", "FAILED", "CANCELED"]);

export function parsePersistedJson(value, fallback = {}) {
  if (value && typeof value === "object") return value;
  try { return JSON.parse(value || ""); } catch { return fallback; }
}

export function approvalVersion(waitingApprovalRef) {
  const match = String(waitingApprovalRef || "").match(/^approval:\/\/([0-9a-f-]{36})$/i);
  return match?.[1] || "";
}

export function budgetRows(run) {
  const budget = parsePersistedJson(run?.budgetJson);
  const usage = parsePersistedJson(run?.checkpointJson).usage || {};
  return [["轮次","rounds","maxRounds"],["工具调用","toolCalls","maxToolCalls"],["候选","candidates","maxCandidates"],["Episodes","episodes","maxEpisodes"],["Tokens","tokens","maxTokens"],["成本(μ)","costMicros","maxCostMicros"],["墙钟(ms)","wallClockMs","maxWallClockMs"],["失败","failures","maxFailures"]].map(([label, used, maximum]) => ({
    label, used: Number(usage[used] || 0), maximum: Number(budget[maximum] || 0), remaining: Math.max(0, Number(budget[maximum] || 0) - Number(usage[used] || 0))
  }));
}

export function waitReason(run) {
  if (run?.status === "WAITING_APPROVAL") return `等待真实用户审批 ${approvalVersion(run.waitingApprovalRef) || "候选版本"}`;
  if (run?.status === "WAITING_EXPERIMENT") return "等待已提交的 PlayerRun / Episode 持久化";
  if (run?.status === "FAILED") return run.errorCode || "Director 执行失败";
  if (run?.status === "CANCELED") return "已由用户取消";
  return TERMINAL.has(run?.status) ? "运行已结束" : "Director 正在推进下一轮";
}

export function candidateEvidence(candidate) {
  const evidence = parsePersistedJson(candidate?.evidenceJson);
  return { parameters: parsePersistedJson(candidate?.tuningJson), metrics: evidence.personaMetrics || evidence.metrics || {}, comparison: evidence.comparison || null };
}
