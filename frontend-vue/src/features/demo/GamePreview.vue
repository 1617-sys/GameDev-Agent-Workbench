<template>
  <section class="game-preview">
    <header>
      <div><p class="overline">ARCADE COLLECT</p><h2>{{ validation.config?.metadata?.title || "可玩 Demo" }}</h2></div>
      <StatusPill v-if="validation.valid" :status="pillStatus" :label="statusLabel" />
    </header>
    <p v-if="validation.migrated" class="alert">由 GameConfig 1.0 临时迁移，只读预览不会覆盖历史 Artifact。</p>
    <div v-if="!validation.valid" class="alert danger"><AlertTriangle :size="18" /><span>游戏配置无法运行：{{ validation.errors.map(formatGameConfigError).join("；") }}</span></div>
    <div v-else class="game-stage">
      <div class="game-playfield">
        <div ref="container" class="game-canvas" data-runtime-ready="false" :data-runtime-state="hud.status"></div>
        <div v-if="hud.status !== 'PLAYING'" class="game-overlay" :data-state="hud.status">
          <p>{{ overlayTitle }}</p><small>{{ overlayMessage }}</small>
          <button class="button primary" type="button" @click="primaryAction">{{ primaryLabel }}</button>
        </div>
        <div class="touch-controls" aria-label="触摸方向控制">
          <button class="touch-up" type="button" aria-label="向上移动" @pointerdown.prevent="pressDirection('up')" @pointerup.prevent="releaseDirection('up')" @pointercancel.prevent="releaseDirection('up')" @pointerleave="releaseDirection('up')">↑</button>
          <button class="touch-left" type="button" aria-label="向左移动" @pointerdown.prevent="pressDirection('left')" @pointerup.prevent="releaseDirection('left')" @pointercancel.prevent="releaseDirection('left')" @pointerleave="releaseDirection('left')">←</button>
          <button class="touch-down" type="button" aria-label="向下移动" @pointerdown.prevent="pressDirection('down')" @pointerup.prevent="releaseDirection('down')" @pointercancel.prevent="releaseDirection('down')" @pointerleave="releaseDirection('down')">↓</button>
          <button class="touch-right" type="button" aria-label="向右移动" @pointerdown.prevent="pressDirection('right')" @pointerup.prevent="releaseDirection('right')" @pointercancel.prevent="releaseDirection('right')" @pointerleave="releaseDirection('right')">→</button>
        </div>
      </div>
      <aside class="game-hud">
        <span>{{ hud.objective }}</span>
        <div class="hud-stats">
          <div><small>目标</small><strong>{{ hud.collected }}/{{ hud.total }}</strong></div>
          <div><small>得分</small><strong>{{ hud.score }}</strong></div>
          <div><small>生命</small><strong>{{ hud.health }}</strong></div>
          <div><small>时间</small><strong>{{ hud.remainingSeconds }}s</strong></div>
        </div>
        <p class="exit-state" :class="{ unlocked: hud.exitUnlocked }">出口：{{ hud.exitUnlocked ? "已解锁" : "未解锁" }}</p>
        <small class="hud-message" aria-live="polite">{{ hud.message || hud.controls }}</small>
        <div class="runtime-actions">
          <button v-if="hud.status === 'PLAYING' || hud.status === 'PAUSED'" class="button ghost" type="button" @click="togglePause">{{ hud.status === "PAUSED" ? "继续" : "暂停" }}</button>
          <button class="button ghost" type="button" @click="restart"><RotateCcw :size="15" />重开</button>
        </div>
        <p v-for="warning in warnings" :key="warning" class="resource-warning"><AlertTriangle :size="14" />{{ warning }}</p>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { AlertTriangle, RotateCcw } from "@lucide/vue";
import StatusPill from "../../shared/ui/StatusPill.vue";
import { formatGameConfigError, validateGameConfig } from "./runtime/gameConfig";
import { mountGeneratedGame } from "./runtime/topDownCollectRuntime";

const props = defineProps({ config: { type: Object, required: true } });
const container = ref(null);
const hud = ref({ status: "READY", objective: "", controls: "", message: "", collected: 0, total: 0, score: 0, health: 0, remainingSeconds: 0, exitUnlocked: false });
const warnings = ref([]);
const validation = computed(() => validateGameConfig(props.config));
const statusLabel = computed(() => ({ READY: "待开始", PLAYING: "游戏中", PAUSED: "已暂停", WON: "已通关", LOST: "挑战失败" }[hud.value.status] || "可试玩"));
const pillStatus = computed(() => hud.value.status === "LOST" ? "FAILED" : hud.value.status === "PAUSED" || hud.value.status === "READY" ? "PENDING" : "SUCCESS");
const overlayTitle = computed(() => ({ READY: "准备好了吗？", PAUSED: "游戏已暂停", WON: "挑战成功", LOST: "挑战失败" }[hud.value.status] || ""));
const overlayMessage = computed(() => hud.value.message || hud.value.controls);
const primaryLabel = computed(() => hud.value.status === "READY" ? "开始游戏" : hud.value.status === "PAUSED" ? "继续游戏" : "重新挑战");
let runtime = null;

async function mount() {
  runtime?.destroy();
  runtime = null;
  warnings.value = [];
  if (!validation.value.valid) return;
  await nextTick();
  if (!container.value) return;
  runtime = mountGeneratedGame(container.value, validation.value.config, {
    onHud: (value) => { hud.value = value; },
    onWarning: (warning) => { if (!warnings.value.includes(warning)) warnings.value.push(warning); },
    onReady: (runtimeInfo) => {
      container.value?.setAttribute("data-runtime-ready", "true");
      container.value?.setAttribute("data-engine", runtimeInfo.engine || "unknown");
      container.value?.setAttribute("data-renderer", runtimeInfo.renderer || "unknown");
      container.value?.setAttribute("data-physics", runtimeInfo.physics || "unknown");
    }
  });
}

function primaryAction() { runtime?.start(); if (hud.value.status === "PAUSED") runtime?.togglePause(); if (["WON", "LOST"].includes(hud.value.status)) runtime?.restart(); }
function togglePause() { runtime?.togglePause(); }
function restart() { runtime?.restart(); }
function pressDirection(direction) { runtime?.setDirection(direction, true); }
function releaseDirection(direction) { runtime?.setDirection(direction, false); }
function releaseAllDirections() { for (const direction of ["up", "down", "left", "right"]) releaseDirection(direction); }

watch(() => props.config, mount, { deep: true, immediate: true });
onMounted(() => { window.addEventListener("pointerup", releaseAllDirections); window.addEventListener("blur", releaseAllDirections); });
onBeforeUnmount(() => {
  window.removeEventListener("pointerup", releaseAllDirections);
  window.removeEventListener("blur", releaseAllDirections);
  runtime?.destroy();
});
</script>
