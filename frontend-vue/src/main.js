import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { createAppRouter } from "./app/router";
import { useSessionStore } from "./features/auth/sessionStore";
import "./styles/global.css";

const app = createApp(App);
const pinia = createPinia();
const router = createAppRouter();

app.use(pinia);
app.use(router);

const session = useSessionStore(pinia);
router.beforeEach(async (to) => {
  await session.initialize();
  if (to.meta.public && session.authenticated && to.name === "auth") return { name: "projects" };
  if (!to.meta.public && !session.authenticated) return { name: "auth", query: { redirect: to.fullPath } };
  return true;
});

app.mount("#app");
