import { defineStore } from "pinia";
import { knowledgeApi, shouldPollKnowledge } from "../../shared/api/knowledge.js";

export const useKnowledgeStore = defineStore("knowledge", {
  state: () => ({ documents: [], capabilities: {}, loading: false, uploading: false, error: "", pollEpoch: 0 }),
  actions: {
    async load(projectUuid) {
      this.loading = true; this.error = "";
      try { const result = await knowledgeApi.list(projectUuid); this.documents = result.documents || []; this.capabilities = result.capabilities || {}; return result; }
      catch (cause) { this.error = cause.status === 403 ? "没有权限访问项目知识库" : cause.message || "知识库加载失败"; throw cause; }
      finally { this.loading = false; }
    },
    async upload(projectUuid, file) {
      this.uploading = true; this.error = "";
      try { await knowledgeApi.upload(projectUuid, file); await this.load(projectUuid); this.poll(projectUuid); }
      catch (cause) { this.error = cause.message || "知识文件上传失败"; throw cause; }
      finally { this.uploading = false; }
    },
    poll(projectUuid) {
      const epoch = ++this.pollEpoch; let attempts = 0;
      const next = async () => {
        if (epoch !== this.pollEpoch || !shouldPollKnowledge(this.documents) || attempts++ >= 30) return;
        await new Promise(resolve => window.setTimeout(resolve, 2000));
        if (epoch !== this.pollEpoch) return;
        try { await this.load(projectUuid); } catch { return; }
        await next();
      };
      void next();
    },
    stopPolling() { this.pollEpoch += 1; }
  }
});
