<template>
  <main class="standalone-demo">
    <header><div><p class="overline">GAMEFLOW PLAYGROUND</p><h1>{{ config?.title || "可玩 Demo" }}</h1></div><RouterLink class="button ghost" to="/projects"><ArrowLeft :size="16" />返回工作台</RouterLink></header>
    <p v-if="error" class="alert danger">{{ error }}</p>
    <div v-if="loading" class="empty-panel"><LoaderCircle class="spin" :size="24" /><p>正在加载游戏配置…</p></div>
    <GamePreview v-else-if="config" :config="config" />
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ArrowLeft, LoaderCircle } from "@lucide/vue";
import { workflowsApi } from "../../shared/api/workflows";
import { defaultGameConfig, extractGameConfig } from "./runtime/gameConfig";
import GamePreview from "./GamePreview.vue";

const route = useRoute();
const loading = ref(true);
const error = ref("");
const config = ref(null);

onMounted(async () => {
  try {
    if (typeof route.query.artifactUuid === "string") {
      const artifact = await workflowsApi.artifact(route.query.artifactUuid);
      config.value = extractGameConfig(artifact.content) || defaultGameConfig;
    } else config.value = defaultGameConfig;
  } catch (cause) { error.value = cause.message; }
  finally { loading.value = false; }
});
</script>
