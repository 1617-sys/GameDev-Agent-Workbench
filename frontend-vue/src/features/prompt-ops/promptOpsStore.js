import { defineStore } from "pinia";
import { promptOpsApi } from "../../shared/api/promptOps.js";
import { safePromptTemplate, promptUpdatePayload } from "./promptOps.js";

export const usePromptOpsStore = defineStore("prompt-ops", {
  state: () => ({ templates: [], selected: null, loading: false, saving: false, error: "", page: 1, total: 0 }),
  actions: {
    async load(filters = {}) {
      this.loading = true; this.error = "";
      try {
        const result = await promptOpsApi.list(filters);
        this.templates = (result.records || []).map(safePromptTemplate);
        this.page = Number(result.current || 1);
        this.total = Number(result.total || 0);
      } catch (cause) { this.error = cause.message || "Prompt 模板加载失败"; throw cause; }
      finally { this.loading = false; }
    },
    async select(templateUuid) {
      this.error = "";
      try { this.selected = safePromptTemplate(await promptOpsApi.detail(templateUuid)); return this.selected; }
      catch (cause) { this.error = cause.message || "Prompt 模板详情加载失败"; throw cause; }
    },
    async save(template) {
      if (this.saving) return null;
      this.saving = true; this.error = "";
      try {
        this.selected = safePromptTemplate(await promptOpsApi.update(template.templateUuid, promptUpdatePayload(template)));
        return this.selected;
      } catch (cause) { this.error = cause.message || "Prompt 模板保存失败"; throw cause; }
      finally { this.saving = false; }
    }
  }
});
