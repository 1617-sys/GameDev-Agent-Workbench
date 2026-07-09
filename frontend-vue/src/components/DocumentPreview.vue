<template>
  <section class="content-section">
    <div class="section-heading">
      <span>设计文档</span>
      <small>概念 / 核心循环 / 任务拆解</small>
    </div>
    <div class="document-grid">
      <article v-for="doc in docs" :key="doc.key" class="document-card">
        <span class="type-badge">{{ doc.key }}</span>
        <h3>{{ doc.title }}</h3>
        <p class="document-text">{{ doc.content || "等待生成" }}</p>
      </article>
    </div>
  </section>
</template>

<script setup>
defineProps({
  docs: {
    type: Array,
    default: () => []
  }
});
</script>
