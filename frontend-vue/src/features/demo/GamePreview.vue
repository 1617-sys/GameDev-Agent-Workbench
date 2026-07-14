<template>
  <section class="game-preview">
    <header>
      <div><p class="overline">PLAYABLE DEMO</p><h2>{{ validation.config?.title || "可玩 Demo" }}</h2></div>
      <StatusPill v-if="validation.valid" status="SUCCESS" label="可试玩" />
    </header>
    <div v-if="!validation.valid" class="alert danger"><AlertTriangle :size="18" /><span>游戏配置无法运行：{{ validation.errors.join("；") }}</span></div>
    <div v-else class="game-stage">
      <div ref="container" class="game-canvas" data-runtime-ready="false"></div>
      <div class="game-hud"><span>{{ hud.objective }}</span><strong>{{ hud.collected }}/{{ hud.total }}</strong><small>{{ hud.message || hud.controls }}</small></div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { AlertTriangle } from "@lucide/vue";
import StatusPill from "../../shared/ui/StatusPill.vue";
import { validateGameConfig } from "./runtime/gameConfig";
import { mountGeneratedGame } from "./runtime/topDownCollectRuntime";

const props = defineProps({ config: { type: Object, required: true } });
const container = ref(null);
const hud = ref({ objective: "", controls: "", message: "", collected: 0, total: 0 });
const validation = computed(() => validateGameConfig(props.config));
let destroy = null;

async function mount() {
  destroy?.();
  destroy = null;
  if (!validation.value.valid) return;
  await nextTick();
  if (!container.value) return;
  destroy = mountGeneratedGame(container.value, validation.value.config, {
    onHud: (value) => { hud.value = value; },
    onReady: (runtime) => {
      container.value?.setAttribute("data-runtime-ready", "true");
      container.value?.setAttribute("data-engine", runtime.engine || "unknown");
      container.value?.setAttribute("data-renderer", runtime.renderer || "unknown");
      container.value?.setAttribute("data-physics", runtime.physics || "unknown");
    }
  });
}

watch(() => props.config, mount, { deep: true, immediate: true });
onBeforeUnmount(() => destroy?.());
</script>
