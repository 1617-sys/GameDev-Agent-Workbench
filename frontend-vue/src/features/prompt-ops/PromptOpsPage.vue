<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { usePromptOpsStore } from "./promptOpsStore.js";
import { promptDiff } from "./promptOps.js";
import AsyncState from "../../shared/ui/AsyncState.vue";
import DangerConfirm from "../../shared/ui/DangerConfirm.vue";

const store = usePromptOpsStore();
const filters = reactive({ pageNum: 1, pageSize: 20, agentType: "", status: "" });
const draft = ref(null);
const original = ref(null);
const diff = computed(() => original.value && draft.value ? promptDiff(original.value, draft.value) : []);
async function choose(id) { const value = await store.select(id); original.value = structuredClone(value); draft.value = structuredClone(value); }
async function save() { if (!draft.value || !diff.value.length) return; const value = await store.save(draft.value); original.value = structuredClone(value); draft.value = structuredClone(value); await store.load(filters); }
onMounted(() => store.load(filters));
</script>

<template>
  <main class="page-shell">
    <header><p class="eyebrow">管理员 / Prompt 运维</p><h1>Prompt 模板</h1><p class="muted">编辑前显示字段级差异；提交由后端能力校验和审计链负责。</p></header>
    <form class="toolbar" @submit.prevent="store.load(filters)">
      <input v-model="filters.agentType" placeholder="Agent 类型" aria-label="Agent 类型" />
      <select v-model="filters.status" aria-label="状态"><option value="">全部状态</option><option>ACTIVE</option><option>INACTIVE</option></select>
      <button type="submit" :disabled="store.loading">筛选</button>
    </form>
    <AsyncState :loading="store.loading" :error="store.error" :empty="!store.templates.length" empty-text="暂无 Prompt 模板">
      <div class="ops-layout">
        <section class="card"><button v-for="item in store.templates" :key="item.templateUuid" class="list-row" @click="choose(item.templateUuid)"><strong>{{ item.name }}</strong><span>{{ item.agentType }} · v{{ item.version }} · {{ item.status }}</span></button></section>
        <section v-if="draft" class="card form-stack">
          <label>名称<input v-model="draft.name" /></label><label>Agent 类型<input v-model="draft.agentType" /></label>
          <label>System Prompt<textarea v-model="draft.systemPrompt" rows="10" /></label><label>User Prompt 模板<textarea v-model="draft.userPromptTemplate" rows="10" /></label>
          <label>当前版本<input v-model.number="draft.version" type="number" min="1" readonly /></label><label>状态<select v-model="draft.status"><option>ACTIVE</option><option>INACTIVE</option></select></label>
          <aside class="diff-panel" aria-live="polite"><strong>待提交差异</strong><p v-if="!diff.length">无修改</p><ul v-else><li v-for="item in diff" :key="item.field"><code>{{ item.field }}</code>：{{ item.before }} → {{ item.after }}</li></ul></aside>
          <DangerConfirm label="确认更新 Prompt 模板" :disabled="!diff.length || store.saving" @confirm="save" />
        </section>
      </div>
    </AsyncState>
  </main>
</template>
