<template>
  <section class="workflow-run-view">
    <div class="button-row">
      <button class="ghost-button" @click="$emit('back')">← 返回工作台</button>
      <span class="hint safe-wrap">运行 ID: {{ workflowRunUuid }}</span>
    </div>
    <p v-if="run.loading" class="empty-state" aria-live="polite">正在加载服务端运行快照…</p>
    <p v-else-if="run.error && !run.snapshot" class="error content-section" role="alert">{{ run.error.message }}</p>
    <template v-else-if="run.snapshot">
      <section class="content-section run-summary">
        <div><p class="eyebrow">WORKFLOW RUN</p><h2>{{ run.snapshot.status || "UNKNOWN" }}</h2><p class="hint">第 {{ run.snapshot.attempt ?? 0 }} 次尝试 · {{ duration }}</p></div>
        <div class="button-row"><button v-if="allowed('cancel')" class="secondary-button" :disabled="run.actionLoading" @click="command('cancel')">取消</button><button v-if="allowed('retry')" class="primary-button" :disabled="run.actionLoading" @click="command('retry')">重试</button></div>
      </section>
      <p v-if="run.error" class="error" role="alert">{{ run.error.message }}</p>
      <section class="content-section"><h3>步骤</h3><ol v-if="run.steps.length" class="run-steps"><li v-for="step in orderedSteps" :key="step.stepKey"><strong>{{ step.stepKey }}</strong><span>{{ step.status }} · 第 {{ step.attempt ?? 0 }} 次</span><p v-if="step.error">{{ step.error }}</p></li></ol><p v-else class="empty-state">服务端尚未返回步骤。</p></section>
      <section class="content-section"><h3>产物</h3><div v-if="run.artifacts.length" class="artifact-grid"><article v-for="artifact in run.artifacts" :key="artifact.artifactUuid" class="artifact-card"><span class="type-badge">{{ artifact.type }}</span><h4>{{ artifact.displayName || artifact.artifactUuid }}</h4><p>{{ artifact.status }}</p><a v-if="artifact.url && artifact.status === 'AVAILABLE'" class="secondary-button" :href="artifact.url" target="_blank" rel="noopener noreferrer">打开 Demo / 产物</a></article></div><p v-else class="empty-state">暂无可用产物。</p></section>

      <section class="content-section retrieval-section">
        <div class="section-heading"><div><span>检索证据</span><p class="hint">仅显示本次 Agent 实际使用并持久化的来源；不会重新检索或展示文档全文。</p></div><button class="secondary-button" :disabled="evidenceLoading" @click="loadEvidence">刷新证据</button></div>
        <p v-if="evidenceError" class="error" role="alert">{{ evidenceError }}</p>
        <p v-if="evidenceLoading && !evidence.length" class="empty-state">正在读取授权后的检索证据…</p>
        <div v-else-if="evidence.length" class="evidence-list">
          <article v-for="item in evidence" :key="item.agentRunUuid" class="evidence-card">
            <div class="card-title-row"><div><p class="eyebrow safe-wrap">{{ item.stepKey }} · {{ item.agentRunUuid }}</p><h4>{{ ragStateLabel(item) }}</h4></div><span v-if="item.mock" class="status-pill failed">MOCK</span></div>
            <dl class="evidence-meta">
              <div><dt>Retrieval</dt><dd>{{ item.retrievalVersion || "未提供" }}</dd></div>
              <div><dt>Chunking</dt><dd>{{ item.chunkingVersion || "未提供" }}</dd></div>
              <div><dt>Embedding</dt><dd>{{ item.embeddingModel || "未提供" }}</dd></div>
              <div><dt>上下文预算</dt><dd>{{ item.contextBudget ?? "未提供" }}</dd></div>
            </dl>
            <ol v-if="item.references.length" class="reference-list">
              <li v-for="reference in item.references" :key="reference.chunkUuid">
                <strong>#{{ reference.rank }} · score {{ reference.score ?? "缺失" }}</strong>
                <span class="safe-wrap">文档 {{ reference.documentUuid }} · v{{ reference.documentVersion }} · Chunk {{ reference.chunkUuid }}</span>
              </li>
            </ol>
            <p v-else class="empty-state">没有实际注入的 RetrievalRecord。</p>
            <div v-if="item.comparison" class="comparison-block">
              <h5>RAG-on / RAG-off 对照</h5>
              <p class="hint">{{ comparisonStateLabel(item.comparison.status) }} · PromptVersion {{ item.comparison.promptVersionId }} · {{ item.comparison.provider }}/{{ item.comparison.modelName }}</p>
              <p class="hint">统计窗口：{{ formatTime(item.comparison.from) }} 至 {{ formatTime(item.comparison.to) }} · 评测版本：{{ item.comparison.evaluationVersions?.join(" / ") || "缺失" }}</p>
              <div class="comparison-grid">
                <article v-for="cohort in comparisonCohorts(item.comparison)" :key="cohort.label">
                  <strong>{{ cohort.label }}</strong>
                  <span>样本 {{ cohort.data?.samples ?? 0 }} / 已评测 {{ cohort.data?.evaluated ?? 0 }}</span>
                  <span>Schema {{ percent(cohort.data?.schemaPassRate) }} · Rule {{ percent(cohort.data?.rulePassRate) }} · Runtime {{ percent(cohort.data?.runtimePassRate) }}</span>
                  <span>P50/P95 {{ cohort.data?.p50LatencyMs ?? 0 }}/{{ cohort.data?.p95LatencyMs ?? 0 }} ms</span>
                  <span>成本 {{ cohort.data?.estimatedCost ?? "缺失" }} · 缺失成本 {{ cohort.data?.missingCostSamples ?? 0 }}</span>
                  <span v-if="cohort.label === 'RAG-on'">空检索 {{ cohort.data?.emptyRetrieval ?? 0 }} · 失败 {{ cohort.data?.failedRetrieval ?? 0 }}</span>
                </article>
              </div>
              <p class="hint safe-wrap">版本：{{ versionSummary(item.comparison) }}。该结果仅描述固定条件下的样本，不构成绝对质量结论。</p>
            </div>
            <p v-else class="hint">尚无满足固定输入、Prompt、模型和版本条件的对照摘要。</p>
          </article>
        </div>
        <p v-else class="empty-state">此 WorkflowRun 尚无可展示的 Agent RAG 证据。</p>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { comparisonStateLabel, percent, ragStateLabel } from "../utils/ragPresentation";

const props = defineProps({
  store: { type: Object, required: true },
  evidenceApi: { type: Function, required: true },
  workflowRunUuid: { type: String, required: true }
});
defineEmits(["back"]);
const run = computed(() => props.store.ensure(props.workflowRunUuid));
const orderedSteps = computed(() => [...run.value.steps].sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0)));
const duration = computed(() => run.value.snapshot?.startedAt && run.value.snapshot?.finishedAt ? `${Math.max(0, new Date(run.value.snapshot.finishedAt) - new Date(run.value.snapshot.startedAt))} ms` : "等待完成");
const evidence = ref([]);
const evidenceLoading = ref(false);
const evidenceError = ref("");
const allowed = (action) => run.value.snapshot?.allowedActions?.includes(action);

async function command(action) { try { await props.store[action](props.workflowRunUuid); } catch {} }
async function loadEvidence() {
  evidenceLoading.value = true;
  evidenceError.value = "";
  try { evidence.value = await props.evidenceApi(props.workflowRunUuid) || []; }
  catch (cause) { evidenceError.value = cause.message || "无法读取检索证据"; }
  finally { evidenceLoading.value = false; }
}
function comparisonCohorts(comparison) { return [{ label: "RAG-off", data: comparison.ragOff }, { label: "RAG-on", data: comparison.ragOn }]; }
function versionSummary(comparison) { return [comparison.retrievalVersion, comparison.chunkingVersion, comparison.embeddingModel].filter(Boolean).join(" / ") || "缺失"; }
function formatTime(value) { return value ? new Date(value).toLocaleString() : "缺失"; }

onMounted(() => { props.store.open(props.workflowRunUuid); loadEvidence(); });
onBeforeUnmount(() => props.store.disconnect(props.workflowRunUuid));
</script>
