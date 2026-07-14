<template>
  <section class="preview-shell">
    <div class="preview-header">
      <div>
        <strong>{{ validation.config?.title || "游戏预览" }}</strong>
        <p>由已验证的 GameConfig 在固定 Phaser Runtime 中渲染。</p>
      </div>
      <button class="secondary-button" :disabled="!canPlay" @click="restart">重新开始</button>
    </div>

    <div v-if="!validation.valid" class="config-error" role="alert">
      <strong>GameConfig 不可用于预览</strong>
      <p v-for="error in validation.errors" :key="error">{{ error }}</p>
    </div>

    <div v-else-if="runtimeError" class="config-error" role="alert">
      <strong>游戏预览无法启动</strong>
      <p>{{ runtimeError }}</p>
    </div>

    <div v-else class="game-layout">
      <div ref="gameContainer" class="game-canvas" :data-runtime-ready="runtimeReady ? 'true' : 'false'"></div>
      <aside class="game-hud">
        <span :class="['status-pill', hud.status.toLowerCase()]">{{ hud.status }}</span>
        <h3>{{ hud.objective }}</h3>
        <p>收集进度：{{ hud.collected }}/{{ hud.total }}</p>
        <p>操作：{{ hud.controls }}</p>
        <div class="runtime-message">{{ hud.message }}</div>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { mountGeneratedGame } from "../game/topDownCollectRuntime";
import { validateGameConfig } from "../game/gameConfig";

const props = defineProps({ gameConfig: { type: [Object, String], default: null } });
const gameContainer = ref(null);
const runtimeReady = ref(false);
const runtimeError = ref("");
let destroyGame = null;
const validation = computed(() => validateGameConfig(props.gameConfig));
const canPlay = computed(() => validation.value.valid);
const hud = reactive({ status: "READY", objective: "", controls: "", message: "等待试玩", collected: 0, total: 0 });

function cleanup() {
  if (destroyGame) {
    destroyGame();
    destroyGame = null;
  }
}

async function restart() {
  cleanup();
  runtimeReady.value = false;
  runtimeError.value = "";
  if (!gameContainer.value || !validation.value.valid) return;
  const config = validation.value.config;
  hud.objective = config.ui.objective;
  hud.controls = config.ui.controls;
  hud.total = config.items.length;
  await nextTick();
  gameContainer.value.innerHTML = "";
  try {
    destroyGame = mountGeneratedGame(gameContainer.value, config, {
      onHud: (payload) => Object.assign(hud, payload),
      onReady: () => { runtimeReady.value = true; }
    });
  } catch {
    runtimeError.value = "已验证的 GameConfig 无法在当前浏览器启动。";
  }
}

watch(validation, restart, { deep: true });
onMounted(restart);
onBeforeUnmount(cleanup);
</script>
