import { defineStore } from "pinia";
import { projectsApi } from "../../shared/api/projects";

export const useProjectsStore = defineStore("projects", {
  state: () => ({ items: [], loading: false, creating: false, error: "", loaded: false }),
  actions: {
    async load(force = false) {
      if (this.loading || (this.loaded && !force)) return;
      this.loading = true;
      this.error = "";
      try {
        this.items = await projectsApi.list();
        this.loaded = true;
      } catch (error) { this.error = error.message; }
      finally { this.loading = false; }
    },
    async create(input) {
      this.creating = true;
      this.error = "";
      try {
        const project = await projectsApi.create(input);
        this.items = [project, ...this.items.filter((item) => item.projectUuid !== project.projectUuid)];
        this.loaded = true;
        return project;
      } catch (error) { this.error = error.message; throw error; }
      finally { this.creating = false; }
    }
  }
});
