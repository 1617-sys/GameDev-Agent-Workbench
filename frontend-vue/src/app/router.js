import { createRouter, createWebHistory } from "vue-router";
import { appRoutes } from "./routes";
export function createAppRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: appRoutes,
    scrollBehavior: () => ({ top: 0 })
  });
}
