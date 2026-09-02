<template>
  <div class="page-stack episode-page">
    <header class="page-heading"><RouterLink class="back-link" :to="`/projects/${projectUuid}/versions`">← 返回原型版本</RouterLink><p class="overline">MACHINE EVIDENCE</p><h1>Player Runs 与 Episode 轨迹</h1><p>版本 {{ versionUuid }} · 所有指标来自服务端持久化证据。</p></header>
    <p v-if="error" class="alert danger" role="alert">{{ error }}</p>
    <div v-if="loading" class="empty-panel">正在读取 Player Runs…</div>
    <div v-else-if="!runs.length" class="empty-panel"><h2>暂无 Player Run</h2><p>为此 PrototypeVersion 提交 Player 对照运行后，证据会显示在这里。</p></div>
    <template v-else>
      <section v-if="aggregate" class="evidence-grid metrics"><article><small>机器样本</small><strong>{{ aggregate.sampleSize }}</strong></article><article><small>完成率</small><strong>{{ percent(aggregate.completionRate) }}</strong></article><article><small>平均耗时</small><strong>{{ aggregate.averageDurationMs }} ms</strong></article><article><small>平均动作</small><strong>{{ decimal(aggregate.averageActionCount) }}</strong></article></section>
      <div class="evidence-layout">
        <aside class="run-list"><button v-for="run in runs" :key="run.runUuid" :class="{active:selectedRun?.runUuid===run.runUuid}" @click="selectRun(run)"><strong>{{ run.clientBatchKey }}</strong><span>{{ run.status }} · 尝试 {{ run.attempt }}</span><small>{{ stamp(run.createdAt) }}</small></button></aside>
        <main class="trace-panel">
          <section v-if="selectedRun" :class="['run-detail-state', `tone-${selectedRunView.tone}`]">
            <strong>{{ selectedRunView.title }}</strong><p>{{ selectedRunView.detail }}</p>
            <dl><div><dt>Run UUID</dt><dd><code>{{ selectedRun.runUuid }}</code></dd></div><div><dt>尝试次数</dt><dd>{{ selectedRun.attempt }}</dd></div><div><dt>创建时间</dt><dd>{{ stamp(selectedRun.createdAt) }}</dd></div><div><dt>完成时间</dt><dd>{{ stamp(selectedRun.completedAt) }}</dd></div></dl>
          </section>
          <div v-if="selectedRun && !selectedRun.persistedBatchUuid" class="empty-panel"><h2>{{ selectedRunView.title }}</h2><p>{{ selectedRunView.detail }}</p></div>
          <template v-else-if="batch">
            <header class="batch-heading"><div><p class="overline">BATCH {{ batch.status }}</p><h2>{{ batch.clientBatchKey }}</h2></div><span>{{ batch.completed }}/{{ batch.total }} 完成</span></header>
            <div v-if="batchView.empty" class="empty-panel">{{ batchView.message }}</div>
            <div class="episode-tabs"><button v-for="item in batch.items" :key="item.episodeId" :class="{active:episode?.episodeId===item.episodeId}" @click="selectEpisode(item.episodeId)">{{ item.personaId }} · {{ item.executionStatus }}</button></div>
            <template v-if="episode">
              <section class="evidence-grid"><article><small>Persona / Policy</small><strong>{{ episode.personaId }} {{ episode.personaVersion }}</strong><span>{{ episode.policyId }} {{ episode.policyVersion }}</span></article><article><small>结果</small><strong>{{ episode.outcome || episode.executionStatus }}</strong><span>{{ episode.terminationReason || episode.error?.code || "—" }}</span></article><article><small>模型与用量</small><strong>{{ episode.model?.model || "不适用" }}</strong><span>{{ usageText(episode.usage) }}</span></article><article><small>终态证据</small><strong>{{ episode.finalScore ?? "—" }} 分</strong><code>{{ shortHash(episode.finalStateHash) }}</code></article></section>
              <p v-if="episode.audit?.mock" class="alert danger">此 Episode 使用明确标记的 mock 模型，不计入真实 LLM 指标。</p>
              <div v-if="stepPage?.items?.length" class="step-list"><article v-for="step in stepPage.items" :key="step.sequence"><header><strong>Step {{ step.sequence }}</strong><span>{{ step.decision?.requestedAction?.type || "INVALID" }} · {{ step.decision?.policyDurationMs }} ms</span></header><dl><div><dt>Observation digest</dt><dd><code>{{ step.observationDigest }}</code></dd></div><div><dt>状态哈希</dt><dd><code>{{ step.transition?.previousStateHash }} → {{ step.transition?.stateHash }}</code></dd></div><div><dt>反馈</dt><dd>{{ feedback(step) }}</dd></div><div v-if="step.transition?.error"><dt>错误</dt><dd>{{ step.transition.error.code }}</dd></div></dl></article></div>
              <div v-else class="empty-panel">此 Episode 没有可显示的 Step。</div>
              <nav v-if="stepPage?.total > stepPage?.size" class="pagination"><button :disabled="!stepPagination.hasPrevious" @click="loadSteps(stepPage.page-1)">上一页</button><span>{{ stepPage.page+1 }} / {{ stepPagination.totalPages }}</span><button :disabled="!stepPagination.hasNext" @click="loadSteps(stepPage.page+1)">下一页</button></nav>
            </template>
          </template>
        </main>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { episodesApi } from "../../shared/api/episodes";
import { playerRunPresentation } from "./playerRunPresentation.js";
import { batchPresentation, episodePagination } from "./episodePresentation.js";
const route=useRoute();const projectUuid=computed(()=>String(route.params.projectUuid));const versionUuid=computed(()=>String(route.params.versionUuid));
const runs=ref([]),selectedRun=ref(null),batch=ref(null),episode=ref(null),stepPage=ref(null),aggregate=ref(null),loading=ref(true),error=ref("");
const selectedRunView=computed(()=>playerRunPresentation(selectedRun.value));
const batchView=computed(()=>batchPresentation(batch.value));const stepPagination=computed(()=>episodePagination(stepPage.value||{page:0,size:50,total:0}));
onMounted(load);
async function load(){loading.value=true;error.value="";try{[runs.value,aggregate.value]=await Promise.all([episodesApi.runs(projectUuid.value,versionUuid.value),episodesApi.aggregate(projectUuid.value,versionUuid.value)]);if(runs.value.length)await selectRun(runs.value[0]);}catch(cause){error.value=message(cause,"无法读取 Episode 证据");}finally{loading.value=false;}}
async function selectRun(run){batch.value=null;episode.value=null;stepPage.value=null;try{selectedRun.value=await episodesApi.run(projectUuid.value,run.runUuid);if(!selectedRun.value.persistedBatchUuid)return;batch.value=await episodesApi.batch(projectUuid.value,selectedRun.value.persistedBatchUuid);if(batch.value.items?.length)await selectEpisode(batch.value.items[0].episodeId);}catch(cause){error.value=message(cause,"无法读取完整 Player Run 或批次");}}
async function selectEpisode(id){try{episode.value=await episodesApi.detail(projectUuid.value,id);await loadSteps(0);}catch(cause){error.value=message(cause,"无法读取完整 Machine Episode 详情");}}
async function loadSteps(page){try{stepPage.value=await episodesApi.steps(projectUuid.value,episode.value.episodeId,page,50);}catch(cause){error.value=message(cause,"无法读取分页轨迹");}}
function feedback(step){const t=step.transition||{};return `${t.accepted?"已接受":"已拒绝"} · 分数 ${Number(t.scoreDelta||0)>=0?"+":""}${t.scoreDelta||0} · ${(t.events||[]).map(e=>e.type).join(", ")||"无事件"}`;}
function usageText(usage){if(!usage||usage.status==="NOT_APPLICABLE")return "无需模型";if(usage.status!=="REPORTED")return `不可用：${usage.unavailableReason||"未知"}`;return `${usage.totalTokens} tokens · ${usage.costMicros??"成本未知"}${usage.currency?` ${usage.currency}`:""}`;}
function shortHash(value){return value?`${value.slice(0,12)}…`:"—";}function percent(v){return `${Math.round(Number(v||0)*100)}%`;}function decimal(v){return Number(v||0).toFixed(1);}function stamp(v){return v?new Date(v).toLocaleString("zh-CN"):"—";}function message(cause,fallback){return cause?.status===403?"没有权限查看此项目证据":cause?.message||fallback;}
</script>

<style scoped>
.evidence-layout{display:grid;grid-template-columns:minmax(220px,280px) 1fr;gap:20px}.run-list,.trace-panel,.step-list{display:grid;gap:12px}.run-list button,.episode-tabs button{display:grid;text-align:left;gap:4px;padding:12px;border:1px solid var(--border-color,#d9dde7);border-radius:10px;background:var(--surface,#fff)}.run-list button.active,.episode-tabs button.active{border-color:#6366f1;background:#eef2ff}.evidence-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.evidence-grid article,.step-list article{padding:14px;border:1px solid var(--border-color,#d9dde7);border-radius:12px;background:var(--surface,#fff);overflow:hidden}.evidence-grid article{display:grid;gap:5px}.episode-tabs{display:flex;gap:8px;overflow:auto;margin:12px 0}.batch-heading,.step-list header,.pagination{display:flex;justify-content:space-between;align-items:center;gap:12px}.step-list dl{display:grid;gap:8px}.step-list dl div{display:grid;grid-template-columns:150px minmax(0,1fr);gap:10px}.step-list code{overflow-wrap:anywhere}.pagination{justify-content:center;margin-top:16px}@media(max-width:760px){.evidence-layout{grid-template-columns:1fr}.run-list{grid-template-columns:repeat(2,minmax(0,1fr))}.evidence-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.step-list dl div{grid-template-columns:1fr}}@media(max-width:480px){.run-list,.evidence-grid{grid-template-columns:1fr}}
.run-detail-state{border:1px solid #d9dde7;border-radius:12px;display:grid;gap:8px;padding:14px}.run-detail-state p,.run-detail-state dl{margin:0}.run-detail-state dl{display:grid;gap:5px;grid-template-columns:repeat(2,minmax(0,1fr))}.run-detail-state dl div{min-width:0}.run-detail-state dt{color:#697588;font-size:11px}.run-detail-state dd{margin:2px 0 0;overflow-wrap:anywhere}.run-detail-state.tone-danger{background:#fff2f0;border-color:#efb4aa}.run-detail-state.tone-success{background:#eefaf5;border-color:#a9dfc8}
</style>
