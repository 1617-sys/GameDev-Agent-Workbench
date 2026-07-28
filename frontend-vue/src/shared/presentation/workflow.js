export const TERMINAL_STATUSES = new Set(["SUCCESS", "FAILED", "TIMEOUT", "CANCELED"]);

export const STATUS_META = {
  PENDING: { label: "等待中", tone: "neutral" },
  QUEUED: { label: "排队中", tone: "neutral" },
  RUNNING: { label: "正在生成", tone: "info" },
  SUCCESS: { label: "已完成", tone: "success" },
  FAILED: { label: "生成失败", tone: "danger" },
  TIMEOUT: { label: "生成超时", tone: "warning" },
  CANCELED: { label: "已取消", tone: "warning" },
  CANCEL_REQUESTED: { label: "正在取消", tone: "warning" }
};

export const STEP_LABELS = {
  game_concept: "游戏概念",
  core_loop_design: "核心玩法",
  task_breakdown: "开发计划",
  game_config_generate: "游戏配置"
};

export const ARTIFACT_LABELS = {
  GAME_CONCEPT_RESULT: "游戏概念",
  CORE_LOOP_DESIGN_RESULT: "核心玩法",
  TASK_BREAKDOWN_RESULT: "开发计划",
  GAME_CONFIG: "游戏配置",
  GAME_CONFIG_GENERATE_RESULT: "游戏配置",
  PHASER_GAME_CONFIG: "游戏配置",
  RESOURCE_MANIFEST: "资源清单"
};

export function statusMeta(status) {
  return STATUS_META[status] || { label: status || "未知", tone: "neutral" };
}

export function stepLabel(key) {
  return STEP_LABELS[key] || key || "未命名步骤";
}

export function artifactLabel(type, fallback) {
  return ARTIFACT_LABELS[type] || fallback || type || "生成结果";
}

export function formatDuration(milliseconds) {
  const value = Number(milliseconds);
  if (!Number.isFinite(value) || value < 1) return "--";
  if (value < 1000) return `${Math.round(value)} ms`;
  const seconds = Math.round(value / 1000);
  if (seconds < 60) return `${seconds} 秒`;
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
}
