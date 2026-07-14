<template>
  <section class="content-section">
    <div class="section-heading">
      <div><span>生成步骤</span><p class="hint">进度以服务端保存的步骤状态为准。</p></div>
      <small>{{ completedCount }}/{{ steps.length }} 已完成</small>
    </div>
    <ol v-if="steps.length" class="run-steps">
      <li v-for="step in orderedSteps" :key="step.stepKey">
        <strong>{{ step.stepKey }}</strong>
        <span>{{ step.status || "PENDING" }} · 第 {{ step.attempt ?? 0 }} 次</span>
        <p v-if="step.error?.message" class="error">{{ step.error.message }}</p>
      </li>
    </ol>
    <p v-else class="empty-state">服务端尚未返回步骤信息。</p>
  </section>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({ steps: { type: Array, default: () => [] } });
const orderedSteps = computed(() => [...props.steps].sort((left, right) => (left.stepOrder ?? 0) - (right.stepOrder ?? 0)));
const completedCount = computed(() => props.steps.filter((step) => step.status === "SUCCESS").length);
</script>
