export function ragStateLabel(evidence) {
  if (!evidence?.ragEnabled) return "RAG 已关闭";
  if (evidence.mock) return `Mock · ${ragStatus(evidence.ragStatus)}`;
  return ragStatus(evidence.ragStatus);
}

function ragStatus(status) {
  if (status === "AVAILABLE") return "已使用检索来源";
  if (status === "EMPTY") return "空候选：未注入来源";
  if (status === "UNAVAILABLE") return "检索失败：已降级运行";
  return status || "状态未提供";
}

export function comparisonStateLabel(status) {
  if (status === "COMPARABLE") return "同条件样本可比较";
  if (status === "INSUFFICIENT_SAMPLES") return "样本不足";
  if (status === "INCOMPARABLE_VERSION_MIX") return "版本不一致，禁止比较";
  return "尚无对照结果";
}

export function percent(value) {
  return value === null || value === undefined ? "缺失" : `${(value * 100).toFixed(1)}%`;
}
