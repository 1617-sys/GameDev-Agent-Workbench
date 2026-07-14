<template>
  <div class="page-stack run-page">
    <RouterLink class="back-link" to="/projects"><ArrowLeft :size="16" />返回项目中心</RouterLink>

    <section class="run-header">
      <div class="run-title">
        <p class="overline">WORKFLOW RUN</p>
        <div><h1>{{ meta.label }}</h1><StatusPill :status="run.snapshot?.status" /></div>
        <p>第 {{ run.snapshot?.attempt || 1 }} 次尝试 · {{ formatDuration(run.snapshot?.timeTakenMs) }}</p>
      </div>
      <div class="run-actions">
        <button class="button ghost" type="button" :disabled="run.loading" @click="run.load()"><RefreshCw :class="{ spin: run.loading }" :size="17" />刷新状态</button>
        <button v-if="allowed('cancel')" class="button danger-outline" type="button" :disabled="run.actionLoading" @click="run.command('cancel')"><Square :size="15" />取消</button>
        <button v-if="allowed('retry')" class="button primary" type="button" :disabled="run.actionLoading" @click="run.command('retry')"><RotateCcw :size="16" />重新生成</button>
      </div>
    </section>

    <p v-if="run.error || run.snapshot?.error" class="alert danger" role="alert"><AlertCircle :size="18" /><span>{{ run.error || run.snapshot?.error?.message }}</span></p>

    <section class="progress-section">
      <div class="progress-summary"><span>生成进度</span><strong>{{ run.completedCount }}/{{ Math.max(run.steps.length, 4) }} 已完成</strong></div>
      <RunStepper :steps="run.steps" />
    </section>

    <nav class="content-tabs" aria-label="运行详情视图">
      <button :class="{ active: tab === 'results' }" type="button" @click="tab = 'results'"><PanelTop :size="17" />成果</button>
      <button :class="{ active: tab === 'progress' }" type="button" @click="tab = 'progress'"><ListChecks :size="17" />生成过程</button>
      <button :class="{ active: tab === 'technical' }" type="button" @click="tab = 'technical'"><Braces :size="17" />技术详情</button>
    </nav>

    <section v-if="tab === 'results'" class="tab-panel">
      <GamePreview v-if="gameConfig" :config="gameConfig" />
      <ArtifactResults :artifacts="run.artifacts" :details="run.artifactDetails" :load-artifact="run.loadArtifact" />
    </section>

    <section v-else-if="tab === 'progress'" class="tab-panel process-list">
      <article v-for="step in run.steps" :key="step.stepKey">
        <div class="process-index">{{ step.stepOrder }}</div>
        <div><strong>{{ stepLabel(step.stepKey) }}</strong><p>{{ step.error?.message || `${statusMeta(step.status).label} · 第 ${step.attempt || 1} 次尝试` }}</p></div>
        <div class="process-meta"><StatusPill :status="step.status" /><span>{{ formatDuration(step.timeTakenMs) }}</span></div>
      </article>
    </section>

    <section v-else class="tab-panel technical-panel">
      <dl class="technical-grid">
        <div><dt>运行 UUID</dt><dd>{{ run.snapshot?.workflowRunUuid || "--" }}</dd></div>
        <div><dt>SSE 连接</dt><dd>{{ connectionLabel }}</dd></div>
        <div><dt>状态版本</dt><dd>{{ run.snapshot?.statusVersion ?? "--" }}</dd></div>
        <div><dt>最后事件序号</dt><dd>{{ run.snapshot?.lastSequence ?? "--" }}</dd></div>
        <div><dt>Schema</dt><dd>{{ run.snapshot?.schemaVersion || "--" }}</dd></div>
        <div><dt>允许操作</dt><dd>{{ run.snapshot?.allowedActions?.join(", ") || "无" }}</dd></div>
      </dl>
    </section>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { AlertCircle, ArrowLeft, Braces, ListChecks, PanelTop, RefreshCw, RotateCcw, Square } from "@lucide/vue";
import StatusPill from "../../shared/ui/StatusPill.vue";
import { extractGameConfig } from "../demo/runtime/gameConfig";
import ArtifactResults from "./ArtifactResults.vue";
import RunStepper from "./RunStepper.vue";
import { useRunStore } from "./runStore";
import { formatDuration, statusMeta, stepLabel } from "../../shared/presentation/workflow";

const route = useRoute();
const GamePreview = defineAsyncComponent(() => import("../demo/GamePreview.vue"));
const run = useRunStore();
const tab = ref("progress");
const meta = computed(() => statusMeta(run.snapshot?.status));
const connectionLabel = computed(() => ({ connected: "实时连接正常", connecting: "正在连接", reconnecting: "正在恢复", idle: run.terminal ? "运行已结束" : "未连接" }[run.connection] || run.connection));
const gameConfig = computed(() => {
  for (const detail of Object.values(run.artifactDetails)) {
    const type = detail?.artifactType || "";
    if (type.includes("GAME_CONFIG")) {
      const config = extractGameConfig(detail.content);
      if (config) return config;
    }
  }
  return null;
});

const allowed = (action) => run.snapshot?.allowedActions?.includes(action);

async function loadGameConfigArtifact() {
  const artifact = run.artifacts.find((item) => String(item.type || item.artifactType).includes("GAME_CONFIG"));
  if (artifact) await run.loadArtifact(artifact.artifactUuid);
}

onMounted(() => run.open(String(route.params.workflowRunUuid)));
watch(() => run.artifacts, async () => {
  await loadGameConfigArtifact();
  if (run.snapshot?.status === "SUCCESS") tab.value = "results";
}, { deep: true });
onBeforeUnmount(() => run.disconnect());
</script>
