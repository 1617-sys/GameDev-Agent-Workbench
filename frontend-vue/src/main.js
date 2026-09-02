import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { createAppRouter } from "./app/router";
import { useSessionStore } from "./features/auth/sessionStore";
import { createRouteGuard } from "./app/routeGuard";
import "./styles/global.css";

const app = createApp(App);
const pinia = createPinia();
const router = createAppRouter();

app.use(pinia);
app.use(router);

const session = useSessionStore(pinia);
router.beforeEach(createRouteGuard(session));

app.mount("#app");
