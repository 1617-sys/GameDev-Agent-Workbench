<template>
  <section class="preview-shell">
    <div class="preview-header">
      <div>
        <strong>{{ normalized.title }}</strong>
        <p>固定 Phaser3 Runtime 读取 GameConfig 渲染</p>
      </div>
      <div class="button-row">
        <button class="secondary-button" :disabled="!canPlay" @click="restart">重新开始</button>
        <button class="ghost-button" :disabled="!demoUrl" @click="$emit('open-demo')">新窗口试玩</button>
      </div>
    </div>

    <div v-if="!validation.valid" class="config-error">
      <strong>GameConfig 校验失败</strong>
      <p v-for="error in validation.errors" :key="error">{{ error }}</p>
      <button class="secondary-button" @click="$emit('regenerate-config')">重新生成 GameConfig</button>
    </div>

    <div v-else class="game-layout">
      <div ref="gameContainer" class="game-canvas"></div>
      <aside class="game-hud">
        <span :class="['status-pill', hud.status.toLowerCase()]">{{ hud.status }}</span>
        <h3>{{ hud.objective }}</h3>
        <p>收集进度：{{ hud.collected }}/{{ hud.total }}</p>
        <p>操作：{{ hud.controls }}</p>
        <div class="runtime-message">{{ hud.message }}</div>
      </aside>
    </div>

    <JsonViewer :value="normalized" label="查看 GameConfig" />
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import JsonViewer from "./JsonViewer.vue";
import { mountGeneratedGame } from "../game/topDownCollectRuntime";
import { defaultGameConfig, normalizeGameConfig, validateGameConfig } from "../game/gameConfig";

const props = defineProps({
  gameConfig: {
    type: Object,
    default: null
  },
  demoUrl: {
    type: String,
    default: ""
  }
});

defineEmits(["open-demo", "regenerate-config"]);

const gameContainer = ref(null);
let destroyGame = null;

const normalized = computed(() => normalizeGameConfig(props.gameConfig || defaultGameConfig));
const validation = computed(() => validateGameConfig(normalized.value));
const canPlay = computed(() => validation.value.valid);

const hud = reactive({
  status: "READY",
  objective: normalized.value.ui.objective,
  controls: normalized.value.ui.controls,
  message: "等待试玩",
  collected: 0,
  total: normalized.value.items.length
});

watch(normalized, () => {
  hud.objective = normalized.value.ui.objective;
  hud.controls = normalized.value.ui.controls;
  hud.total = normalized.value.items.length;
  restart();
}, { deep: true });

onMounted(() => {
  restart();
});

onBeforeUnmount(() => {
  cleanup();
});

function cleanup() {
  if (destroyGame) {
    destroyGame();
    destroyGame = null;
  }
}

async function restart() {
  cleanup();
  if (!gameContainer.value || !validation.value.valid) return;
  await nextTick();
  gameContainer.value.innerHTML = "";
  destroyGame = mountGeneratedGame(gameContainer.value, normalized.value, {
    onHud: (payload) => Object.assign(hud, payload)
  });
}
</script>
