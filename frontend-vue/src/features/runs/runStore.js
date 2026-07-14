import { defineStore } from "pinia";
import { workflowsApi } from "../../shared/api/workflows";
import { TERMINAL_STATUSES } from "../../shared/presentation/workflow";

export const useRunStore = defineStore("run", {
  state: () => ({
    uuid: "", snapshot: null, steps: [], artifacts: [], artifactDetails: {}, loading: false,
    actionLoading: false, error: "", connection: "idle", stream: null, refreshTimer: null
  }),
  getters: {
    terminal: (state) => TERMINAL_STATUSES.has(state.snapshot?.status),
    completedCount: (state) => state.steps.filter((step) => step.status === "SUCCESS").length
  },
  actions: {
    async load(uuid = this.uuid) {
      if (!uuid) return;
      this.uuid = uuid;
      this.loading = true;
      this.error = "";
      try {
        const [snapshot, steps, artifacts] = await Promise.all([
          workflowsApi.run(uuid), workflowsApi.steps(uuid), workflowsApi.artifacts(uuid)
        ]);
        this.snapshot = snapshot;
        this.steps = Array.isArray(steps) ? steps : snapshot?.steps || [];
        this.artifacts = Array.isArray(artifacts) ? artifacts : snapshot?.artifacts || [];
        if (this.terminal) this.disconnect();
      } catch (error) { this.error = error.message || "无法读取运行状态"; }
      finally { this.loading = false; }
    },
    scheduleRefresh() {
      if (this.refreshTimer !== null) return;
      this.refreshTimer = window.setTimeout(async () => {
        this.refreshTimer = null;
        await this.load();
      }, 120);
    },
    connect() {
      this.disconnect();
      if (!this.uuid || this.terminal) return;
      this.connection = "connecting";
      this.stream = workflowsApi.subscribe(this.uuid, {
        lastEventId: this.snapshot?.lastSequence || 0,
        onEvent: () => { this.connection = "connected"; this.scheduleRefresh(); },
        onError: () => {
          this.connection = "reconnecting";
          this.stream = null;
          window.setTimeout(async () => {
            await this.load();
            if (!this.terminal && this.uuid) this.connect();
          }, 1000);
        }
      });
    },
    async open(uuid) {
      this.disconnect();
      this.$patch({ uuid, snapshot: null, steps: [], artifacts: [], artifactDetails: {}, error: "" });
      await this.load(uuid);
      if (!this.terminal && this.snapshot) this.connect();
    },
    disconnect() {
      this.stream?.close();
      this.stream = null;
      if (this.refreshTimer !== null) window.clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
      if (this.connection !== "idle") this.connection = "idle";
    },
    async loadArtifact(uuid) {
      if (!uuid || this.artifactDetails[uuid]) return this.artifactDetails[uuid];
      const detail = await workflowsApi.artifact(uuid);
      this.artifactDetails = { ...this.artifactDetails, [uuid]: detail };
      return detail;
    },
    async command(action) {
      if (!this.uuid || this.actionLoading) return;
      this.actionLoading = true;
      this.error = "";
      try {
        await workflowsApi[action](this.uuid);
        await this.load();
        if (!this.terminal) this.connect();
      } catch (error) { this.error = error.message || "操作失败"; }
      finally { this.actionLoading = false; }
    }
  }
});
