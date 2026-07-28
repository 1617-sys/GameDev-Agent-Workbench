<template>
  <div class="page-stack prototype-page">
    <header class="page-heading with-action">
      <div>
        <RouterLink class="back-link" :to="`/projects/${projectUuid}/studio`"><ArrowLeft :size="16" />返回创作台</RouterLink>
        <p class="overline">IMMUTABLE PROTOTYPES</p>
        <h1>原型版本与调参</h1>
        <p>每次 AI 生成或人工调参都会形成新的不可变版本。</p>
      </div>
      <button class="button ghost" type="button" :disabled="loading" @click="loadVersions"><RefreshCw :size="16" :class="{ spin: loading }" />刷新版本</button>
    </header>

    <p v-if="error" class="alert danger" role="alert"><AlertCircle :size="18" />{{ error }}</p>
    <div v-if="loading && versions.length === 0" class="empty-panel"><LoaderCircle class="spin" :size="24" /><p>正在读取版本…</p></div>
    <div v-else-if="versions.length === 0" class="empty-panel"><Layers3 :size="28" /><h2>还没有原型版本</h2><p>成功完成一次游戏生成后，已验证配置会自动成为版本 1。</p></div>

    <div v-else class="prototype-layout">
      <aside class="version-sidebar">
        <button v-for="version in versions" :key="version.versionUuid" type="button"
          :class="['version-card', { active: selected?.versionUuid === version.versionUuid }]" @click="selectVersion(version.versionUuid)">
          <span><strong>版本 {{ version.versionNumber }}</strong><small>{{ sourceLabel(version.source) }}</small></span>
          <small>{{ formatTime(version.createdAt) }}</small>
        </button>
      </aside>

      <main class="version-main">
        <section v-if="selected" class="version-summary">
          <header><div><p class="overline">VERSION {{ selected.versionNumber }}</p><h2>{{ sourceLabel(selected.source) }}</h2></div><span class="status-pill tone-success"><span></span>不可变</span></header>
          <dl class="version-parameters">
            <div><dt>时限</dt><dd>{{ selected.parameters.timeLimitSeconds }} 秒</dd></div>
            <div><dt>玩家速度</dt><dd>{{ selected.parameters.playerSpeed }}</dd></div>
            <div><dt>生命</dt><dd>{{ selected.parameters.playerMaxHealth }}</dd></div>
            <div><dt>收集目标</dt><dd>{{ selected.parameters.targetCollectibles }}</dd></div>
            <div><dt>敌人数</dt><dd>{{ selected.parameters.enemyCount }}</dd></div>
            <div><dt>父版本</dt><dd>{{ parentNumber(selected.parentVersionUuid) }}</dd></div>
          </dl>
          <details><summary>追溯信息</summary><code>{{ selected.versionUuid }}</code><code>{{ selected.configDigest }}</code><code>{{ selected.runtimeCapabilityVersion }}</code></details>
          <RouterLink class="button ghost" :to="`/projects/${projectUuid}/versions/${selected.versionUuid}/episodes`">查看 Player Runs 与 Episode 证据</RouterLink>
          <dl v-if="metrics" class="version-parameters telemetry-metrics">
            <div><dt>结束样本</dt><dd>{{ metrics.sampleSize }}</dd></div><div><dt>通关率</dt><dd>{{ percent(metrics.winRate) }}</dd></div>
            <div><dt>平均耗时</dt><dd>{{ duration(metrics.averageDurationMs) }}</dd></div><div><dt>平均得分</dt><dd>{{ metrics.averageScore }}</dd></div>
            <div><dt>平均受击</dt><dd>{{ decimal(metrics.averageHitCount) }}</dd></div><div><dt>失败</dt><dd>{{ failureTotal(metrics) }}</dd></div>
          </dl>
          <button class="button ghost" type="button" :disabled="!metrics?.sufficientForAi || suggesting" @click="requestSuggestion">{{ suggesting ? "正在评测…" : "生成 AI 平衡建议" }}</button>
          <button class="button primary" type="button" :disabled="exporting || !suggestion" @click="exportPackage">{{ exportButtonLabel }}</button>
          <button v-if="exportJob?.status === 'FAILED'" class="button ghost" type="button" :disabled="exporting" @click="retryExport">重试同一冻结输入</button>
          <p v-if="exportJob" class="resource-warning">{{ exportStatusText }}</p>
          <p v-else-if="!suggestion" class="resource-warning">生成当前版本的平衡建议后可导出离线原型包。</p>
          <p v-if="metrics && !metrics.sufficientForAi" class="resource-warning">至少需要 5 个已结束会话，当前建议不会夸大少量样本。</p>
          <p v-if="suggestion" class="alert"><strong>{{ suggestion.source }}</strong> · 样本 {{ suggestion.sampleSize }}：{{ suggestion.recommendation }}</p>
        </section>

        <nav class="content-tabs">
          <button :class="{ active: tab === 'play' }" type="button" @click="tab = 'play'"><Play :size="16" />试玩</button>
          <button :class="{ active: tab === 'tune' }" type="button" @click="openTune"><SlidersHorizontal :size="16" />创建调参版本</button>
          <button :class="{ active: tab === 'compare' }" type="button" @click="tab = 'compare'"><GitCompareArrows :size="16" />版本对比</button>
        </nav>

        <GamePreview v-if="tab === 'play' && selectedConfig" :key="selected.versionUuid" :config="selectedConfig" :project-uuid="projectUuid" :version-uuid="selected.versionUuid" />
        <p v-else-if="tab === 'play'" class="alert danger" role="alert"><AlertCircle :size="18" />版本配置校验或摘要校验失败，Runtime 不会挂载。</p>

        <form v-else-if="tab === 'tune'" class="form-stack tuning-panel" @submit.prevent="submitTune">
          <p>从版本 {{ selected.versionNumber }} 创建子版本。仅可修改契约白名单字段。</p>
          <div class="form-grid">
            <label><span>时限（30-600 秒）</span><input v-model.number="tuning.timeLimitSeconds" type="number" min="30" max="600" /></label>
            <label><span>玩家速度（80-400）</span><input v-model.number="tuning.playerSpeed" type="number" min="80" max="400" /></label>
            <label><span>生命（1-5）</span><input v-model.number="tuning.playerMaxHealth" type="number" min="1" max="5" /></label>
            <label><span>收集目标（1-20）</span><input v-model.number="tuning.targetCollectibles" type="number" min="1" max="20" /></label>
            <label><span>敌人数（0-12）</span><input v-model.number="tuning.enemyCount" type="number" min="0" max="12" /></label>
          </div>
          <fieldset v-if="enemyEntries.length" class="enemy-speed-editor"><legend>现有敌人速度（20-240）</legend><label v-for="enemy in enemyEntries" :key="enemy.id"><span>{{ enemy.id }}</span><input v-model.number="tuning.enemySpeeds[enemy.id]" type="number" min="20" max="240" /></label></fieldset>
          <button class="button primary" type="submit" :disabled="saving"><Save :size="16" />{{ saving ? "正在创建…" : "创建不可变子版本" }}</button>
        </form>

        <section v-else class="compare-panel">
          <div class="compare-selectors">
            <label>左侧版本<select v-model="compareLeft"><option v-for="version in versions" :key="version.versionUuid" :value="version.versionUuid">版本 {{ version.versionNumber }}</option></select></label>
            <label>右侧版本<select v-model="compareRight"><option v-for="version in versions" :key="version.versionUuid" :value="version.versionUuid">版本 {{ version.versionNumber }}</option></select></label>
            <button class="button ghost" type="button" :disabled="comparing || !compareLeft || !compareRight" @click="runCompare">对比</button>
          </div>
          <table v-if="comparison"><thead><tr><th>参数</th><th>版本 {{ comparison.left.versionNumber }} · {{ sourceLabel(comparison.left.source) }}</th><th>版本 {{ comparison.right.versionNumber }} · {{ sourceLabel(comparison.right.source) }}</th></tr></thead><tbody><tr v-for="item in comparison.differences" :key="item.key" :class="{ changed: item.changed }"><td>{{ parameterLabel(item.key) }}</td><td>{{ displayValue(item.leftValue) }}</td><td>{{ displayValue(item.rightValue) }}</td></tr></tbody></table>
          <table v-if="metricComparison"><thead><tr><th>试玩指标</th><th>左侧</th><th>右侧</th></tr></thead><tbody>
            <tr><td>样本</td><td>{{ metricComparison.left.sampleSize }}</td><td>{{ metricComparison.right.sampleSize }}</td></tr><tr><td>通关率</td><td>{{ percent(metricComparison.left.winRate) }}</td><td>{{ percent(metricComparison.right.winRate) }}</td></tr>
            <tr><td>平均耗时</td><td>{{ duration(metricComparison.left.averageDurationMs) }}</td><td>{{ duration(metricComparison.right.averageDurationMs) }}</td></tr><tr><td>失败</td><td>{{ failureTotal(metricComparison.left) }}</td><td>{{ failureTotal(metricComparison.right) }}</td></tr>
          </tbody></table>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { AlertCircle, ArrowLeft, GitCompareArrows, Layers3, LoaderCircle, Play, RefreshCw, Save, SlidersHorizontal } from "@lucide/vue";
import { prototypesApi } from "../../shared/api/prototypes";
import { telemetryApi } from "../../shared/api/telemetry";
import { exportsApi, saveExport } from "../../shared/api/exports";
import { createIdempotencyKey } from "../../shared/presentation/submission";
import { sha256Hex, validateGameConfig } from "../demo/runtime/gameConfig";
import { formatPackageSize, waitForExportTerminal } from "./exportState";
import GamePreview from "../demo/GamePreview.vue";

const route = useRoute();
const projectUuid = computed(() => String(route.params.projectUuid));
const versions = ref([]); const selected = ref(null); const loading = ref(false); const saving = ref(false); const comparing = ref(false);
const metrics = ref(null); const metricComparison = ref(null); const suggestion = ref(null); const suggesting = ref(false);
const exportJob = ref(null); const exportActivity = ref("IDLE");
let exportEpoch = 0; let exportController = null;
const error = ref(""); const tab = ref("play"); const comparison = ref(null); const compareLeft = ref(""); const compareRight = ref("");
const tuning = reactive({ timeLimitSeconds: 90, playerSpeed: 220, playerMaxHealth: 3, targetCollectibles: 1, enemyCount: 0, enemySpeeds: {} });
const selectedConfig = computed(() => {
  const result = validateGameConfig(selected.value?.gameConfig);
  return result.valid && !result.migrated
    && sha256Hex(selected.value.gameConfig) === selected.value.configDigest ? result.config : null;
});
const enemyEntries = computed(() => Object.entries(selected.value?.parameters?.enemySpeeds || {}).map(([id, speed]) => ({ id, speed })));
const exporting = computed(() => exportActivity.value !== "IDLE");
const exportButtonLabel = computed(() => ({ CREATING: "正在创建…", POLLING: "正在组装…", DOWNLOADING: "正在下载…", RETRYING: "正在重试…" })[exportActivity.value]
  || (exportJob.value?.status === "COMPLETED" ? "重新下载原型包" : "导出离线原型包"));
const exportStatusText = computed(() => {
  const job = exportJob.value; if (!job) return "";
  if (job.status === "FAILED") return `导出失败 · 尝试 ${job.attemptCount} · ${exportErrorLabel(job.errorCode)}`;
  if (job.status === "PENDING") return `导出处理中 · 尝试 ${job.attemptCount}`;
  return `导出完成 · ${formatPackageSize(job.packageSize)} · SHA-256 ${job.packageDigest}`;
});

onMounted(loadVersions);
onBeforeUnmount(cancelExportFlow);
async function loadVersions() {
  loading.value = true; error.value = "";
  try {
    versions.value = await prototypesApi.list(projectUuid.value);
    if (versions.value.length) await selectVersion(selected.value?.versionUuid || versions.value[0].versionUuid);
    compareLeft.value ||= versions.value.at(-1)?.versionUuid || "";
    compareRight.value ||= versions.value[0]?.versionUuid || "";
  } catch (cause) { error.value = cause.message || "无法读取版本"; }
  finally { loading.value = false; }
}
async function selectVersion(uuid) {
  cancelExportFlow();
  try { selected.value = await prototypesApi.get(projectUuid.value, uuid); metrics.value = await telemetryApi.metrics(projectUuid.value, uuid); suggestion.value=null; exportJob.value=null; resetTuning(); }
  catch (cause) { error.value = cause.message || "无法读取版本详情"; }
}
function resetTuning() {
  const p = selected.value?.parameters; if (!p) return;
  Object.assign(tuning, { timeLimitSeconds: p.timeLimitSeconds, playerSpeed: p.playerSpeed, playerMaxHealth: p.playerMaxHealth, targetCollectibles: p.targetCollectibles, enemyCount: p.enemyCount, enemySpeeds: { ...p.enemySpeeds } });
}
function openTune() { resetTuning(); tab.value = "tune"; }
async function submitTune() {
  if (!selected.value || saving.value) return; saving.value = true; error.value = "";
  try {
    const created = await prototypesApi.tune(projectUuid.value, selected.value.versionUuid, { ...tuning, enemySpeeds: { ...tuning.enemySpeeds } }, createIdempotencyKey());
    await loadVersions(); await selectVersion(created.versionUuid); tab.value = "play";
  } catch (cause) { error.value = cause.message || "创建调参版本失败"; }
  finally { saving.value = false; }
}
async function runCompare() {
  comparing.value = true; error.value = "";
  try { comparison.value = await prototypesApi.compare(projectUuid.value, compareLeft.value, compareRight.value); metricComparison.value=await telemetryApi.compare(projectUuid.value, compareLeft.value, compareRight.value); }
  catch (cause) { error.value = cause.message || "版本对比失败"; }
  finally { comparing.value = false; }
}
function sourceLabel(source) { return source === "TUNED" ? "人工调参" : "AI 生成"; }
function parentNumber(uuid) { return versions.value.find((item) => item.versionUuid === uuid)?.versionNumber || "首版本"; }
function formatTime(value) { return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "--"; }
function parameterLabel(key) { return ({ timeLimitSeconds: "时限", playerSpeed: "玩家速度", playerMaxHealth: "生命", targetCollectibles: "收集目标", enemyCount: "敌人数", enemySpeeds: "敌人速度" })[key] || key; }
function displayValue(value) { return typeof value === "object" ? JSON.stringify(value) : String(value ?? "--"); }
async function requestSuggestion(){suggesting.value=true;error.value="";try{suggestion.value=await telemetryApi.suggest(projectUuid.value,selected.value.versionUuid,createIdempotencyKey());}catch(cause){error.value=cause.message||"平衡评测失败";}finally{suggesting.value=false;}}
async function exportPackage() {
  if (exporting.value || !selected.value) return;
  if (exportJob.value?.status === "COMPLETED") { await downloadExport(exportJob.value); return; }
  await runExport(
    () => exportsApi.create(projectUuid.value, selected.value.versionUuid, createIdempotencyKey()),
    "CREATING"
  );
}
async function retryExport() {
  if (exporting.value || exportJob.value?.status !== "FAILED") return;
  await runExport(() => exportsApi.retry(projectUuid.value, exportJob.value.jobUuid), "RETRYING");
}
async function runExport(start, activity) {
  cancelExportFlow(false);
  const epoch = exportEpoch; const versionUuid = selected.value.versionUuid;
  exportController = new AbortController(); exportActivity.value = activity; error.value = "";
  try {
    let job = await start();
    if (!isCurrentExport(epoch, versionUuid)) return;
    exportJob.value = job;
    if (job.status === "PENDING") exportActivity.value = "POLLING";
    job = await waitForExportTerminal(job, {
      signal: exportController.signal,
      load: (jobUuid) => exportsApi.get(projectUuid.value, jobUuid)
    });
    if (!isCurrentExport(epoch, versionUuid)) return;
    exportJob.value = job;
    if (job.status === "COMPLETED") await downloadExport(job, epoch, versionUuid);
  } catch (cause) {
    if (cause.name !== "AbortError" && isCurrentExport(epoch, versionUuid)) error.value = cause.message || "原型包导出失败";
  } finally {
    if (isCurrentExport(epoch, versionUuid)) exportActivity.value = "IDLE";
  }
}
async function downloadExport(job, epoch = exportEpoch, versionUuid = selected.value?.versionUuid) {
  exportActivity.value = "DOWNLOADING"; error.value = "";
  try {
    const file = await exportsApi.download(projectUuid.value, job.jobUuid, job.packageName);
    if (isCurrentExport(epoch, versionUuid)) saveExport(file);
  } catch (cause) {
    if (isCurrentExport(epoch, versionUuid)) error.value = cause.message || "原型包下载失败";
  } finally {
    if (isCurrentExport(epoch, versionUuid)) exportActivity.value = "IDLE";
  }
}
function cancelExportFlow(resetActivity = true) {
  exportEpoch += 1; exportController?.abort(); exportController = null;
  if (resetActivity) exportActivity.value = "IDLE";
}
function isCurrentExport(epoch, versionUuid) { return epoch === exportEpoch && selected.value?.versionUuid === versionUuid; }
function exportErrorLabel(code) { return ({ EXPORT_INPUT_INCOMPLETE: "导出输入不完整", EXPORT_SECURITY_REJECTED: "安全校验未通过", EXPORT_RETRY_EXHAUSTED: "重试次数已耗尽", EXPORT_BUILD_FAILED: "原型包组装失败" })[code] || code || "未知错误"; }
function percent(value){return `${Math.round(Number(value||0)*100)}%`;} function duration(value){return `${Math.round(Number(value||0)/1000)} 秒`;} function decimal(value){return Number(value||0).toFixed(1);} function failureTotal(value){return Object.values(value?.failures||{}).reduce((sum,n)=>sum+Number(n||0),0);}
</script>
