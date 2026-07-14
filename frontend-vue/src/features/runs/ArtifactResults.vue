<template>
  <div v-if="loading" class="empty-panel compact"><LoaderCircle class="spin" :size="22" /><p>正在读取生成成果…</p></div>
  <div v-else-if="artifacts.length === 0" class="empty-panel compact"><PackageOpen :size="26" /><p>成果将在对应步骤完成后显示。</p></div>
  <div v-else class="results-layout">
    <nav class="result-tabs" aria-label="生成成果">
      <button v-for="artifact in artifacts" :key="artifact.artifactUuid" type="button" :class="{ active: selectedUuid === artifact.artifactUuid }" @click="selectArtifact(artifact)">
        <FileText :size="17" /><span>{{ artifactLabel(artifact.type || artifact.artifactType, artifact.displayName || artifact.title) }}</span><CheckCircle2 :size="15" />
      </button>
    </nav>
    <section class="result-content">
      <div v-if="selectedDetail" class="artifact-document">
        <header><div><p class="overline">GENERATED ARTIFACT</p><h2>{{ artifactLabel(selectedDetail.artifactType, selectedDetail.title) }}</h2></div><StatusPill status="SUCCESS" label="已生成" /></header>
        <pre>{{ readableContent(selectedDetail.content) }}</pre>
      </div>
      <div v-else class="empty-panel compact"><LoaderCircle class="spin" :size="22" /><p>正在打开成果…</p></div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { CheckCircle2, FileText, LoaderCircle, PackageOpen } from "@lucide/vue";
import StatusPill from "../../shared/ui/StatusPill.vue";
import { artifactLabel } from "../../shared/presentation/workflow";

const props = defineProps({ artifacts: { type: Array, default: () => [] }, details: { type: Object, default: () => ({}) }, loadArtifact: { type: Function, required: true } });
const selectedUuid = ref("");
const loading = ref(false);
const selectedDetail = computed(() => props.details[selectedUuid.value] || null);

function readableContent(content) {
  if (!content) return "暂无内容";
  if (typeof content !== "string") return JSON.stringify(content, null, 2);
  try {
    const parsed = JSON.parse(content);
    return parsed.content || parsed.summary || parsed.text || JSON.stringify(parsed, null, 2);
  } catch { return content; }
}

async function selectArtifact(artifact) {
  selectedUuid.value = artifact.artifactUuid;
  if (!selectedDetail.value) {
    loading.value = true;
    try { await props.loadArtifact(artifact.artifactUuid); } finally { loading.value = false; }
  }
}

async function selectFirst() { if (props.artifacts[0] && !selectedUuid.value) await selectArtifact(props.artifacts[0]); }
onMounted(selectFirst);
watch(() => props.artifacts, selectFirst, { deep: true });
</script>
