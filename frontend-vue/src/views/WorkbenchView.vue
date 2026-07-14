<template>
  <section class="workbench-view content-section">
    <div class="section-heading">
      <div>
        <p class="eyebrow">项目工作台</p>
        <span>{{ project.name }}</span>
        <p class="hint">{{ project.description }}</p>
      </div>
      <button class="secondary-button" type="button" :disabled="submitting" @click="$emit('back')">返回项目</button>
    </div>

    <div class="workspace-overview">
      <div><small>游戏类型</small><strong>{{ project.gameType }}</strong></div>
      <div><small>目标平台</small><strong>{{ project.targetPlatform }}</strong></div>
      <div><small>项目状态</small><strong>{{ project.status || "已创建" }}</strong></div>
    </div>

    <form class="workflow-form" @submit.prevent="submit">
      <label>游戏想法<textarea v-model="form.idea" :disabled="submitting" required maxlength="5000" rows="6" placeholder="描述想制作的游戏玩法、主题和核心体验。" /></label>
      <label>补充上下文（可选）<textarea v-model="form.context" :disabled="submitting" maxlength="5000" rows="3" placeholder="例如目标玩家、视觉风格或已有的世界观。" /></label>
      <div class="button-row">
        <button class="primary-button" :disabled="submitting">{{ submitting ? "正在提交…" : "开始生成" }}</button>
        <button type="button" class="secondary-button" :disabled="submitting" @click="$emit('open-knowledge', project.projectUuid)">管理项目知识库</button>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from "vue";
import { prepareWorkflowSubmission } from "../utils/workflowSubmission";

const props = defineProps({
  api: { type: Object, required: true },
  project: { type: Object, required: true }
});
const emit = defineEmits(["back", "submitted", "open-knowledge"]);
const form = reactive({ idea: "", context: "" });
const submitting = ref(false);
const error = ref("");
let pendingSubmission = null;

async function submit() {
  if (submitting.value) return;
  const prepared = prepareWorkflowSubmission(form, pendingSubmission);
  if (prepared.validationError) {
    error.value = prepared.validationError;
    return;
  }
  pendingSubmission = prepared.pendingSubmission;
  submitting.value = true;
  error.value = "";
  try {
    const result = await props.api.submit(props.project.projectUuid, prepared.request, pendingSubmission.idempotencyKey);
    if (!result?.workflowRunUuid) throw new Error("提交响应缺少运行标识。");
    pendingSubmission = null;
    emit("submitted", result.workflowRunUuid);
  } catch (cause) {
    error.value = cause.message || "提交失败，请检查内容后重试。";
  } finally {
    submitting.value = false;
  }
}
</script>
