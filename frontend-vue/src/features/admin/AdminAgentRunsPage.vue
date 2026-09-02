<script setup>
import { onMounted, reactive, ref } from "vue";
import { useAgentRunsStore } from "./agentRunsStore.js";
import { agentRunPayload } from "./agentRunsAdmin.js";
import AsyncState from "../../shared/ui/AsyncState.vue";
const store = useAgentRunsStore();
const filters = reactive({ pageNum: 1, pageSize: 20, projectUuid: "", agentType: "", status: "" });
const form = reactive({ projectUuid: "", agentType: "DESIGNER", title: "", content: "", context: "", ragEnabled: false, ragTopK: 5, ragContextBudget: 8000 });
const confirmed = ref(false);
async function create() { await store.create(agentRunPayload(form, confirmed.value)); await store.load(filters); }
onMounted(() => store.load(filters));
</script>
<template><main class="page-shell"><header><p class="eyebrow">管理员 / Agent Runs</p><h1>受控 Agent 工作区</h1></header>
  <section class="card"><h2>创建运行</h2><form class="form-stack" @submit.prevent="create"><input v-model="form.projectUuid" placeholder="项目 UUID" required /><input v-model="form.agentType" placeholder="Agent 类型" required /><input v-model="form.title" maxlength="200" placeholder="标题" required /><textarea v-model="form.content" placeholder="内容" required /><textarea v-model="form.context" maxlength="2000" placeholder="上下文（可选）" /><label><input v-model="form.ragEnabled" type="checkbox" /> 启用 RAG</label><label><input v-model="confirmed" type="checkbox" /> 我确认本次调用可能产生模型成本</label><button :disabled="store.creating || !confirmed">{{ store.creating ? '创建中…' : '创建 Agent Run' }}</button></form></section>
  <form class="toolbar" @submit.prevent="store.load(filters)"><input v-model="filters.projectUuid" placeholder="项目 UUID" /><input v-model="filters.agentType" placeholder="Agent 类型" /><input v-model="filters.status" placeholder="状态" /><button>筛选</button></form>
  <AsyncState :loading="store.loading" :error="store.error" :empty="!store.runs.length" empty-text="暂无 Agent Run"><div class="table-scroll"><table><thead><tr><th>Run</th><th>项目</th><th>类型</th><th>状态</th><th>模型</th><th>耗时</th></tr></thead><tbody><tr v-for="run in store.runs" :key="run.runUuid" @click="store.detail(run.runUuid)"><td><code>{{ run.runUuid }}</code></td><td>{{ run.projectUuid }}</td><td>{{ run.agentType }}</td><td>{{ run.status }}</td><td>{{ run.provider || '—' }} / {{ run.modelName || '—' }}</td><td>{{ run.timeTakenMs == null ? '—' : `${run.timeTakenMs} ms` }}</td></tr></tbody></table></div></AsyncState>
  <section v-if="store.selected" class="card"><h2>运行详情</h2><pre>{{ store.selected }}</pre></section>
</main></template>
