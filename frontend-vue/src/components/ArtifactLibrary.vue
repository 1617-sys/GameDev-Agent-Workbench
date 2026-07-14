<template>
  <section class="content-section">
    <div class="section-heading">
      <div><span>生成结果</span><p class="hint">仅展示服务端确认可用的产物。</p></div>
      <small>{{ artifacts.length }} 个产物</small>
    </div>
    <p v-if="artifacts.length === 0" class="empty-state">暂时没有可用产物。生成完成后会在这里显示结果。</p>
    <div v-else class="artifact-grid">
      <article v-for="artifact in artifacts" :key="artifact.artifactUuid" class="artifact-card">
        <span class="type-badge">{{ artifact.type }}</span>
        <h3>{{ artifact.displayName || artifact.type }}</h3>
        <p>{{ artifact.status || "UNKNOWN" }}</p>
      </article>
    </div>
  </section>
</template>

<script setup>
defineProps({ artifacts: { type: Array, default: () => [] } });
</script>
