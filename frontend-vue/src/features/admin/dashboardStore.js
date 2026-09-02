import { defineStore } from "pinia";
import { dashboardApi } from "../../shared/api/dashboard.js";
import { dashboardModel } from "./dashboardPresentation.js";
export const useDashboardStore = defineStore("admin-dashboard", {
  state: () => ({ projects: [], agentTypes: [], loading: false, error: "" }),
  actions: { async load() { this.loading = true; this.error = ""; try { const [projects, agents] = await Promise.all([dashboardApi.projects(), dashboardApi.agentTypes()]); Object.assign(this, dashboardModel(projects, agents)); } catch (cause) { this.error = cause.message || "运营汇总加载失败"; throw cause; } finally { this.loading = false; } } }
});
