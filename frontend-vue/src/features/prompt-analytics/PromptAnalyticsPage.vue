<script setup>
import { onMounted, reactive } from "vue";
import { usePromptAnalyticsStore } from "./promptAnalyticsStore.js";
import { metricDisplay } from "./promptAnalytics.js";
import AsyncState from "../../shared/ui/AsyncState.vue";

const now = new Date();
const earlier = new Date(now.getTime() - 30 * 86400_000);
const local = value => new Date(value.getTime() - value.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
const filters = reactive({ from: local(earlier), to: local(now), projectId: "", agentType: "", includeMock: false });
const store = usePromptAnalyticsStore();
onMounted(() => store.load(filters));
</script>

<template>
  <main class="page-shell">
    <header><p class="eyebrow">管理员 / Prompt 指标</p><h1>Prompt Version 聚合指标</h1><p class="muted">数值与缺失标记均直接来自后端聚合。</p></header>
    <form class="toolbar" @submit.prevent="store.load(filters)">
      <label>开始<input v-model="filters.from" type="datetime-local" required /></label><label>结束<input v-model="filters.to" type="datetime-local" required /></label>
      <label>项目 ID<input v-model="filters.projectId" type="number" min="1" /></label><label>Agent 类型<input v-model="filters.agentType" /></label>
      <label><input v-model="filters.includeMock" type="checkbox" /> 包含 Mock</label><button type="submit" :disabled="store.loading">查询</button>
    </form>
    <AsyncState :loading="store.loading" :error="store.error" :empty="!store.metrics.length" empty-text="当前筛选范围没有样本">
      <div class="table-scroll"><table><thead><tr><th>版本</th><th>调用</th><th>成功率</th><th>平均 / P50 / P95</th><th>Tokens</th><th>成本</th><th>样本</th><th>告警</th></tr></thead>
        <tbody><tr v-for="metric in store.metrics" :key="metric.promptVersionId"><template v-for="display in [metricDisplay(metric)]" :key="metric.promptVersionId"><td>{{ display.promptVersionId }}</td><td>{{ display.calls }}</td><td>{{ display.successRate }}</td><td>{{ display.meanLatency }} / {{ display.p50 }} / {{ display.p95 }}</td><td>{{ display.tokens }}</td><td>{{ display.cost }}</td><td>{{ display.samples }}</td><td>{{ display.warnings }}</td></template></tr></tbody></table></div>
    </AsyncState>
  </main>
</template>
