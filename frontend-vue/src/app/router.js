import { createRouter, createWebHistory } from "vue-router";
export function createAppRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: "/login", name: "auth", component: () => import("../features/auth/AuthPage.vue"), meta: { public: true } },
      { path: "/projects", name: "projects", component: () => import("../features/projects/ProjectsPage.vue") },
      { path: "/projects/:projectUuid/studio", name: "studio", component: () => import("../features/studio/StudioPage.vue") },
      { path: "/runs/:workflowRunUuid", name: "run", component: () => import("../features/runs/RunPage.vue") },
      { path: "/demo/play", name: "demo", component: () => import("../features/demo/DemoPage.vue"), meta: { public: true } },
      { path: "/:pathMatch(.*)*", redirect: "/projects" }
    ],
    scrollBehavior: () => ({ top: 0 })
  });
}
