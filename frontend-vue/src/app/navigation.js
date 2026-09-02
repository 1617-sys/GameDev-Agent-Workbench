const sections = [
  {
    key: "user",
    label: "普通用户",
    items: [
      { key: "projects", label: "项目中心", capability: "projects.read", to: () => "/projects" },
      { key: "generation", label: "Cocos 生成台", capability: "generation.read", project: true, to: p => `/projects/${p}/studio` },
      { key: "artifacts", label: "Artifact 总览", capability: "artifacts.read", project: true, to: p => `/projects/${p}/artifacts` }
    ]
  },
  {
    key: "advanced",
    label: "项目高级",
    items: [
      { key: "prototype-versions", label: "原型版本", capability: "prototype-versions.manage", project: true, to: p => `/projects/${p}/versions` },
      { key: "player-runs", label: "Player Runs", capability: "player-runs.read", project: true, to: p => `/projects/${p}/player-runs` },
      { key: "knowledge", label: "项目知识库", capability: "knowledge.read", project: true, to: p => `/projects/${p}/knowledge` },
      { key: "workflow-runs", label: "Workflow Runs", capability: "workflow-runs.manage", project: true, to: p => `/projects/${p}/workflow-runs` },
      { key: "director-runs", label: "Director Runs", capability: "director-runs.manage", project: true, to: p => `/projects/${p}/director` }
    ]
  },
  {
    key: "admin",
    label: "管理员 / 诊断",
    items: [
      { key: "dashboard", label: "运营总览", capability: "admin.dashboard", to: () => "/admin/dashboard" },
      { key: "agent-runs", label: "Agent Runs", capability: "admin.agent-runs", to: () => "/admin/agent-runs" },
      { key: "prompt-ops", label: "Prompt 运维", capability: "prompt-ops.manage", to: () => "/admin/prompt-ops" },
      { key: "analytics", label: "Prompt 指标", capability: "prompt-analytics.read", to: () => "/admin/analytics" },
      { key: "diagnostics", label: "系统诊断", capability: "admin.diagnostics", to: () => "/admin/diagnostics" }
    ]
  }
];

export function visibleNavigation(capabilities = [], context = {}) {
  const allowed = new Set(capabilities);
  return sections.map(section => ({
    key: section.key,
    label: section.label,
    items: section.items
      .filter(item => allowed.has(item.capability) && (!item.project || context.projectUuid))
      .map(item => ({ ...item, to: item.to(context.projectUuid) }))
  })).filter(section => section.items.length > 0);
}
