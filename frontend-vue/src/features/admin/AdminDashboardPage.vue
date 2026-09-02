<script setup>
import { onMounted } from "vue";
import { useDashboardStore } from "./dashboardStore.js";
import AsyncState from "../../shared/ui/AsyncState.vue";
const store = useDashboardStore();
onMounted(() => store.load());
</script>
<template>
  <main class="page-shell"><header><p class="eyebrow">管理员 / 运营</p><h1>运营总览</h1></header>
    <AsyncState :loading="store.loading" :error="store.error" :empty="!store.projects.length && !store.agentTypes.length" empty-text="暂无运营汇总数据">
      <section><h2>项目运行</h2><div class="summary-grid"><article v-for="item in store.projects" :key="item.projectUuid" class="card"><h3 class="truncate-name">{{ item.projectName }}</h3><p>总计 {{ item.totalRunCount }} · 成功 {{ item.successRunCount }} · 失败 {{ item.failedRunCount }}</p><small>最近运行：{{ item.lastRunTime || '—' }}</small></article></div></section>
      <section><h2>Agent 类型</h2><div class="summary-grid"><article v-for="item in store.agentTypes" :key="item.agentType" class="card"><h3 class="truncate-name">{{ item.agentType }}</h3><p>总计 {{ item.totalCount }} · 成功 {{ item.successCount }} · 失败 {{ item.failedCount }}</p><small>平均耗时：{{ item.avgTimeTakenMs == null ? '缺失' : `${item.avgTimeTakenMs} ms` }}</small></article></div></section>
    </AsyncState>
  </main>
</template>
