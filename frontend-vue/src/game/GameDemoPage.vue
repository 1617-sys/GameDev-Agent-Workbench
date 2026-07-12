<template>
  <main class="demo-page">
    <header class="demo-topbar">
      <div>
        <p class="eyebrow">PLAYABLE DEMO</p>
        <h1>{{ gameConfig.title }}</h1>
        <p>{{ query.projectUuid || "local-preview" }}</p>
      </div>
      <div class="button-row">
        <button class="primary-button" @click="goBack">返回工作台</button>
      </div>
    </header>

    <PhaserGamePreview :game-config="gameConfig" :demo-url="''" />

    <section class="content-section">
      <div class="section-heading">
        <span>Demo 配置</span>
        <small>默认折叠</small>
      </div>
      <JsonViewer :value="gameConfig" label="查看 GameConfig" />
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from "vue";
import JsonViewer from "../components/JsonViewer.vue";
import PhaserGamePreview from "../components/PhaserGamePreview.vue";
import { defaultGameConfig } from "./gameConfig";

const gameConfig = ref(defaultGameConfig);
const query = reactive(Object.fromEntries(new URLSearchParams(window.location.search)));

function goBack() {
  window.location.href = "/";
}
</script>
