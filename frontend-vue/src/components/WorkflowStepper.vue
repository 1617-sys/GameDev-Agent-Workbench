<template>
  <aside class="stepper-panel">
    <div class="section-heading">
      <span>Workflow Stepper</span>
      <small>{{ completedCount }}/{{ steps.length }}</small>
    </div>
    <ol class="stepper">
      <li v-for="step in steps" :key="step.key" :class="['step', stepStatus(step.key).toLowerCase()]">
        <span class="step-dot"></span>
        <div>
          <strong>{{ step.label }}</strong>
          <p>{{ stepMessage(step.key) }}</p>
        </div>
      </li>
    </ol>
  </aside>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  events: {
    type: Array,
    default: () => []
  },
  configValid: {
    type: Boolean,
    default: false
  },
  demoReady: {
    type: Boolean,
    default: false
  }
});

const steps = [
  { key: "GAME_CONCEPT", label: "游戏概念" },
  { key: "TASK_BREAKDOWN", label: "任务拆解" },
  { key: "CORE_LOOP_DESIGN", label: "核心循环" },
  { key: "GAME_CONFIG_GENERATE", label: "GameConfig 校验" },
  { key: "DEMO_READY", label: "试玩 Demo" }
];

const completedCount = computed(() => steps.filter((step) => stepStatus(step.key) === "SUCCESS").length);

function latestEvent(stage) {
  if (stage === "DEMO_READY") {
    return props.events.find((event) => ["COMPLETED", "GAME_BUILD", "DEMO_READY"].includes(event.stage));
  }
  return props.events.find((event) => event.stage === stage);
}

function stepStatus(stage) {
  if (stage === "GAME_CONFIG_GENERATE" && props.configValid) return "SUCCESS";
  if (stage === "DEMO_READY" && props.demoReady) return "SUCCESS";
  const event = latestEvent(stage);
  if (!event) return "WAITING";
  if (event.status === "FAILED") return "FAILED";
  if (event.status === "SUCCESS") return "SUCCESS";
  return "RUNNING";
}

function stepMessage(stage) {
  const status = stepStatus(stage);
  if (status === "WAITING") return "等待执行";
  if (stage === "GAME_CONFIG_GENERATE" && props.configValid) return "配置合法，可进入试玩";
  if (stage === "DEMO_READY" && props.demoReady) return "Demo 已生成";
  return latestEvent(stage)?.message || status;
}
</script>
