<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import catalog from "../../shared/api/diagnosticCatalog.json";
import { diagnosticsApi } from "../../shared/api/diagnostics.js";
import { diagnosticActions, safeHealth } from "./diagnostics.js";
const router = useRouter(); const health = ref({ status: "LOADING", version: "unknown" }); const error = ref("");
const profile = import.meta.env.PROD ? "prod" : "non-prod";
const actions = diagnosticActions(profile, catalog.endpoints);
onMounted(async () => { try { health.value = safeHealth(await diagnosticsApi.health()); } catch (cause) { error.value = cause.message; health.value = { status: "DOWN", version: "unknown" }; } });
</script>
<template><main class="page-shell"><header><p class="eyebrow">管理员 / 诊断</p><h1>系统诊断</h1></header>
  <section class="card"><h2>运行状态</h2><p>健康：<strong>{{ health.status }}</strong> · 版本：{{ health.version }} · Profile：<code>{{ profile }}</code></p><p v-if="error" class="error-text">{{ error }}</p><button v-for="action in actions" :key="action.path" @click="router.push(action.path)">{{ action.label }}</button></section>
  <section><h2>保留的内部 / 废弃接口</h2><div class="table-scroll"><table><thead><tr><th>Lifecycle</th><th>端点</th><th>危险</th><th>所有者</th><th>保留理由</th><th>替代路径</th></tr></thead><tbody><tr v-for="item in catalog.endpoints" :key="`${item.method} ${item.path}`"><td>{{ item.lifecycle }}</td><td><code>{{ item.method }} {{ item.path }}</code></td><td>{{ item.dangerLevel }}</td><td>{{ item.owner }}</td><td>{{ item.retentionReason }}</td><td><code>{{ item.replacement }}</code></td></tr></tbody></table></div></section>
</main></template>
