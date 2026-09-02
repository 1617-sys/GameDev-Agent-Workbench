export function playerRunPresentation(run) {
  if (run?.status === "FAILED") {
    return { tone: "danger", title: "运行失败", detail: `${run.errorCode || "UNKNOWN"}：${run.errorMessage || "后端未提供详情"}` };
  }
  if (run?.status === "RUNNING" || run?.status === "PENDING") {
    return { tone: "info", title: "运行中", detail: "Player 正在执行，尚未生成持久化 Episode。" };
  }
  if (!run?.persistedBatchUuid) {
    return { tone: "warning", title: "运行完成但没有 Episode", detail: "后端未返回 persistedBatchUuid，不会伪造轨迹。" };
  }
  return { tone: "success", title: "运行完成", detail: `Episode 批次 ${run.persistedBatchUuid} 已持久化。` };
}
