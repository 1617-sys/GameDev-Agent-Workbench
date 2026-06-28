import { createApp } from "vue";
import App from "./App.vue";
import GameDemoPage from "./game/GameDemoPage.vue";
import "./styles.css";

const rootComponent = window.location.pathname.startsWith("/demo/play")
  ? GameDemoPage
  : App;

createApp(rootComponent).mount("#app");
