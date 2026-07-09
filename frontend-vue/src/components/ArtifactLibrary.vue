<template>
  <section class="content-section">
    <div class="section-heading">
      <span>产物库</span>
      <small>{{ artifacts.length }} 个产物</small>
    </div>
    <div v-if="artifacts.length === 0" class="empty-state">暂无产物。完成 Demo Workflow 后会自动沉淀设计文档和 GameConfig。</div>
    <div v-else class="artifact-grid">
      <article v-for="artifact in artifacts" :key="artifact.artifactUuid" class="artifact-card">
        <span class="type-badge">{{ artifact.artifactType }}</span>
        <h3>{{ artifact.title || artifact.artifactType }}</h3>
        <p>{{ previewText(artifact) }}</p>
        <JsonViewer :value="artifact" label="查看 JSON" />
      </article>
    </div>
  </section>
</template>

<script setup>
import JsonViewer from "./JsonViewer.vue";
import { artifactText } from "../game/gameConfig";

defineProps({
  artifacts: {
    type: Array,
    default: () => []
  }
});

function previewText(artifact) {
  return artifactText(artifact).slice(0, 180);
}
</script>
