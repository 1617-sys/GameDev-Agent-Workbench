const text = value => String(value || "—").slice(0, 120);
const count = value => Number.isFinite(Number(value)) ? Number(value) : 0;

export function safeProjectSummary(source = {}) {
  return { projectId: source.projectId ?? null, projectUuid: String(source.projectUuid || ""), projectName: text(source.projectName), totalRunCount: count(source.totalRunCount), successRunCount: count(source.successRunCount), failedRunCount: count(source.failedRunCount), lastRunTime: source.lastRunTime ?? null };
}
export function safeAgentSummary(source = {}) {
  return { agentType: text(source.agentType), totalCount: count(source.totalCount), successCount: count(source.successCount), failedCount: count(source.failedCount), avgTimeTakenMs: source.avgTimeTakenMs == null ? null : Number(source.avgTimeTakenMs) };
}
export function dashboardModel(projects, agentTypes) {
  const safeProjects = (projects || []).map(safeProjectSummary);
  const safeAgents = (agentTypes || []).map(safeAgentSummary);
  return { projects: safeProjects, agentTypes: safeAgents, empty: !safeProjects.length && !safeAgents.length };
}
