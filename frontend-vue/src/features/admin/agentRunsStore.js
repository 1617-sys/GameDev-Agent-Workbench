import { defineStore } from "pinia";
import { agentRunsApi } from "../../shared/api/agentRuns.js";
import { agentRunAttempt, safeAgentRun } from "./agentRunsAdmin.js";
export const useAgentRunsStore = defineStore("admin-agent-runs", {
  state: () => ({ runs: [], selected: null, total: 0, loading: false, creating: false, error: "", attempt: null }),
  actions: {
    async load(filters) { this.loading = true; this.error = ""; try { const page = await agentRunsApi.list(filters); this.runs = (page.records || []).map(safeAgentRun); this.total = Number(page.total || 0); } catch (cause) { this.error = cause.message || "Agent Runs 加载失败"; throw cause; } finally { this.loading = false; } },
    async detail(id) { this.selected = safeAgentRun(await agentRunsApi.detail(id)); },
    async create(payload) { if (this.creating) return null; this.attempt = agentRunAttempt(payload, this.attempt); this.creating = true; this.error = ""; try { const result = safeAgentRun(await agentRunsApi.create(payload, this.attempt.key)); this.selected = result; return result; } catch (cause) { this.error = cause.message || "Agent Run 创建失败"; throw cause; } finally { this.creating = false; } }
  }
});
