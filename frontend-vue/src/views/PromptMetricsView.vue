<template>
 <section class="workbench-card metrics-view">
  <div class="button-row"><button class="ghost-button" @click="$emit('back')">← 返回</button><h2>PromptVersion 指标</h2></div>
  <form class="toolbar" @submit.prevent="load"><label>AgentType <input v-model="filters.agentType" placeholder="可选" /></label><label>开始 <input v-model="filters.from" type="datetime-local" required /></label><label>结束 <input v-model="filters.to" type="datetime-local" required /></label><label><input v-model="filters.includeMock" type="checkbox" /> 包含 mock</label><button class="primary-button">查询</button></form>
  <p class="hint">服务端统一口径；默认排除 mock。成本、成功率和 P95 不在浏览器计算。</p>
  <p v-if="error" class="error" role="alert">{{ error }}</p><p v-else-if="loading" class="hint">加载中…</p>
  <p v-else-if="rows.length===0" class="hint">零样本：当前过滤条件下没有真实模型调用。</p>
  <div v-else class="metric-list"><article v-for="row in rows" :key="row.promptVersionId" class="metric-card"><h3>版本 {{ row.promptVersionId }}</h3><p>样本 {{ row.callCount }} · 真实 {{ row.realSampleCount }} · <span :class="{'mock-badge':row.mockSampleCount>0}">mock {{ row.mockSampleCount }}</span></p><p>成功率 {{ percent(row.successRate) }} · P50/P95 {{ value(row.p50LatencyMs,'ms') }} / {{ value(row.p95LatencyMs,'ms') }}</p><p>Token {{ value(row.inputTokens) }} / {{ value(row.outputTokens) }} · 成本 {{ value(row.estimatedCost) }}</p><p v-if="row.latencyMissingCount||row.costMissingCount" class="hint">缺失：延迟 {{ row.latencyMissingCount }}，成本 {{ row.costMissingCount }}</p><p v-if="row.insufficientSample" class="hint">样本不足：P95 仅供参考。</p></article></div>
 </section>
</template>
<script setup>
import { reactive, ref } from "vue";
const props=defineProps({api:{type:Function,required:true}}); defineEmits(["back"]); const now=new Date(); const iso=(d)=>d.toISOString().slice(0,16); const filters=reactive({agentType:"",from:iso(new Date(now-86400000)),to:iso(now),includeMock:false}); const rows=ref([]),loading=ref(false),error=ref(""); const value=(v,u="")=>v===null||v===undefined?"未提供":`${v}${u}`; const percent=(v)=>v===null||v===undefined?"未提供":`${(v*100).toFixed(1)}%`;
async function load(){loading.value=true;error.value="";try{rows.value=await props.api({agentType:filters.agentType||undefined,from:filters.from,to:filters.to,includeMock:String(filters.includeMock)})||[];}catch(e){error.value=e.message||"网络错误";}finally{loading.value=false;}} load();
</script>
