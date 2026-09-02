import { defineStore } from "pinia";
import { promptAnalyticsApi } from "../../shared/api/promptAnalytics.js";
import { safePromptMetric } from "./promptAnalytics.js";

export const usePromptAnalyticsStore = defineStore("prompt-analytics", {
  state: () => ({ metrics: [], loading: false, error: "" }),
  actions: {
    async load(filters) {
      this.loading = true; this.error = "";
      try { this.metrics = (await promptAnalyticsApi.list(filters) || []).map(safePromptMetric); }
      catch (cause) { this.error = cause.message || "Prompt 指标加载失败"; throw cause; }
      finally { this.loading = false; }
    }
  }
});
