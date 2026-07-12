<template>
  <section class="workbench-view content-section">
    <div class="section-heading"><div><span>新建异步工作流</span><p class="hint">提交后立即进入可恢复的运行详情页。</p></div></div>
    <form class="workflow-form" @submit.prevent="submit">
      <label>项目 UUID<input v-model.trim="form.projectUuid" required placeholder="服务端项目 UUID" /></label>
      <label>工作流类型<input v-model.trim="form.workflowKey" required maxlength="80" /></label>
      <label>游戏想法<textarea v-model.trim="form.idea" required maxlength="5000" rows="6" /></label>
      <label>补充上下文（可选）<textarea v-model.trim="form.context" maxlength="5000" rows="3" /></label>
      <div class="button-row"><button class="primary-button" :disabled="submitting">{{ submitting ? "正在提交…" : "创建工作流" }}</button><a class="ghost-button" href="/demo/play">打开 Legacy Demo</a></div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from "vue";

const props = defineProps({ api: { type: Object, required: true } });
const emit = defineEmits(["submitted"]);
const form = reactive({ projectUuid: "", workflowKey: "GAME_GENERATE", idea: "", context: "" });
const submitting = ref(false);
const error = ref("");
let pendingIdempotencyKey = null;

function createIdempotencyKey() { return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`; }
async function submit() {
  if (submitting.value) return;
  pendingIdempotencyKey ||= createIdempotencyKey();
  submitting.value = true;
  error.value = "";
  try {
    const result = await props.api.submit(form.projectUuid, { workflowKey: form.workflowKey, idea: form.idea, context: form.context || null }, pendingIdempotencyKey);
    if (!result?.workflowRunUuid) throw new Error("提交响应缺少 workflowRunUuid");
    emit("submitted", result.workflowRunUuid);
    pendingIdempotencyKey = null;
  } catch (cause) { error.value = cause.message || "提交失败，请使用相同请求重试。"; } finally { submitting.value = false; }
}
</script>
