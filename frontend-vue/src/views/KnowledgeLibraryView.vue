<template>
  <section class="knowledge-library-view">
    <div class="button-row">
      <button class="ghost-button" @click="$emit('back')">← 返回工作台</button>
      <span class="hint safe-wrap">项目：{{ projectUuid }}</span>
    </div>

    <section class="content-section">
      <div class="section-heading">
        <div>
          <span>项目知识库</span>
          <p class="hint">仅显示授权后的状态、版本和短 Hash；不提供全文浏览或下载。</p>
        </div>
        <button class="secondary-button" :disabled="loading" @click="load">刷新</button>
      </div>

      <form class="knowledge-upload" @submit.prevent="upload">
        <label>上传 Markdown、TXT 或 PDF
          <input ref="fileInput" type="file" accept=".md,.markdown,.txt,.pdf,text/markdown,text/plain,application/pdf" required @change="selectFile" />
        </label>
        <button class="primary-button" :disabled="uploading || !selectedFile">
          {{ uploading ? "上传中…" : "上传并异步处理" }}
        </button>
      </form>
      <p class="hint">文件类型、内容、大小和权限最终由服务端校验；上传成功不代表已完成索引。</p>
      <p v-if="message" class="success-message" role="status">{{ message }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </section>

    <section class="content-section">
      <h3>文档状态</h3>
      <p v-if="loading && !documents.length" class="empty-state">正在读取授权后的文档摘要…</p>
      <div v-else-if="documents.length" class="knowledge-grid">
        <article v-for="document in documents" :key="document.documentUuid" class="knowledge-card">
          <div class="card-title-row">
            <h4 class="safe-wrap">{{ document.name }}</h4>
            <span class="status-pill" :class="statusClass(document.status)">{{ document.status }}</span>
          </div>
          <dl class="evidence-meta">
            <div><dt>版本</dt><dd>v{{ document.version ?? "?" }}</dd></div>
            <div><dt>来源</dt><dd>{{ document.sourceType || "未提供" }}</dd></div>
            <div><dt>内容 Hash</dt><dd>{{ document.contentHashSummary || "未提供" }}</dd></div>
            <div><dt>索引时间</dt><dd>{{ formatTime(document.indexedAt) }}</dd></div>
          </dl>
          <p v-if="document.failureSummary" class="error safe-wrap">{{ document.failureSummary }}</p>
          <button class="secondary-button" disabled title="当前后端未开放失效写接口">标记失效（不可用）</button>
        </article>
      </div>
      <p v-else class="empty-state">当前项目没有知识文档。</p>
      <p v-if="!capabilities.invalidate" class="hint">当前后端仅开放上传与只读状态；前端不会伪造失效或删除成功。</p>
    </section>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";

const props = defineProps({
  api: { type: Object, required: true },
  projectUuid: { type: String, required: true }
});
defineEmits(["back"]);

const documents = ref([]);
const capabilities = ref({ upload: true, invalidate: false, delete: false });
const loading = ref(false);
const uploading = ref(false);
const error = ref("");
const message = ref("");
const selectedFile = ref(null);
const fileInput = ref(null);
let pollTimer;

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const response = await props.api.list(props.projectUuid);
    documents.value = response?.documents || [];
    capabilities.value = response?.capabilities || capabilities.value;
    schedulePoll();
  } catch (cause) {
    error.value = cause.message || "无法读取项目知识库";
  } finally {
    loading.value = false;
  }
}

function selectFile(event) {
  selectedFile.value = event.target.files?.[0] || null;
  message.value = "";
}

async function upload() {
  if (!selectedFile.value || uploading.value) return;
  uploading.value = true;
  error.value = "";
  message.value = "";
  try {
    const result = await props.api.upload(props.projectUuid, selectedFile.value);
    message.value = `已创建文档 ${result.documentUuid}，当前状态 ${result.status}。`;
    selectedFile.value = null;
    if (fileInput.value) fileInput.value.value = "";
    await load();
  } catch (cause) {
    error.value = cause.message || "上传失败";
  } finally {
    uploading.value = false;
  }
}

function schedulePoll() {
  clearTimeout(pollTimer);
  if (documents.value.some((document) => ["UPLOADED", "PARSING", "INDEXING"].includes(document.status))) {
    pollTimer = setTimeout(load, 2000);
  }
}

function statusClass(status) {
  if (status === "READY") return "success";
  if (["FAILED", "INVALID", "DELETED"].includes(status)) return "failed";
  return "running";
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : "尚未完成";
}

onMounted(load);
onBeforeUnmount(() => clearTimeout(pollTimer));
</script>
