<template>
  <section class="workflow-run-view">
    <div class="button-row"><button class="ghost-button" @click="$emit('back')">← 返回工作台</button><span class="hint">运行 ID: {{ workflowRunUuid }}</span></div>
    <p v-if="run.loading" class="empty-state" aria-live="polite">正在加载服务端运行快照…</p>
    <p v-else-if="run.error && !run.snapshot" class="error content-section" role="alert">{{ run.error.message }}</p>
    <template v-else-if="run.snapshot">
      <section class="content-section run-summary"><div><p class="eyebrow">WORKFLOW RUN</p><h2>{{ run.snapshot.status || "UNKNOWN" }}</h2><p class="hint">第 {{ run.snapshot.attempt ?? 0 }} 次尝试 · {{ duration }}</p></div><div class="button-row"><button v-if="allowed('cancel')" class="secondary-button" :disabled="run.actionLoading" @click="command('cancel')">取消</button><button v-if="allowed('retry')" class="primary-button" :disabled="run.actionLoading" @click="command('retry')">重试</button></div></section>
      <p v-if="run.error" class="error" role="alert">{{ run.error.message }}</p>
      <section class="content-section"><h3>步骤</h3><ol v-if="run.steps.length" class="run-steps"><li v-for="step in orderedSteps" :key="step.stepKey"><strong>{{ step.stepKey }}</strong><span>{{ step.status }} · 第 {{ step.attempt ?? 0 }} 次</span><p v-if="step.error">{{ step.error }}</p></li></ol><p v-else class="empty-state">服务端尚未返回步骤。</p></section>
      <section class="content-section"><h3>产物</h3><div v-if="run.artifacts.length" class="artifact-grid"><article v-for="artifact in run.artifacts" :key="artifact.artifactUuid" class="artifact-card"><span class="type-badge">{{ artifact.type }}</span><h4>{{ artifact.displayName || artifact.artifactUuid }}</h4><p>{{ artifact.status }}</p><a v-if="artifact.url && artifact.status === 'AVAILABLE'" class="secondary-button" :href="artifact.url" target="_blank" rel="noopener noreferrer">打开 Demo / 产物</a></article></div><p v-else class="empty-state">暂无可用产物。</p></section>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from "vue";

const props = defineProps({ store: { type: Object, required: true }, workflowRunUuid: { type: String, required: true } });
defineEmits(["back"]);
const run = computed(() => props.store.ensure(props.workflowRunUuid));
const orderedSteps = computed(() => [...run.value.steps].sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0)));
const duration = computed(() => run.value.snapshot?.startedAt && run.value.snapshot?.finishedAt ? `${Math.max(0, new Date(run.value.snapshot.finishedAt) - new Date(run.value.snapshot.startedAt))} ms` : "等待完成");
const allowed = (action) => run.value.snapshot?.allowedActions?.includes(action);
async function command(action) { try { await props.store[action](props.workflowRunUuid); } catch {} }
onMounted(() => props.store.loadRun(props.workflowRunUuid));
onBeforeUnmount(() => props.store.disconnect(props.workflowRunUuid));
</script>
