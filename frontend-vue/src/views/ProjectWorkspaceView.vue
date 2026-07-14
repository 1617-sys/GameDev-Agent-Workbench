<template>
  <section class="project-workspace content-section">
    <div class="section-heading">
      <div>
        <span>{{ project.name }}</span>
        <p class="hint">{{ project.description }}</p>
      </div>
      <button class="secondary-button" type="button" @click="$emit('back')">返回项目</button>
    </div>
    <div class="workspace-overview">
      <div><small>游戏类型</small><strong>{{ project.gameType }}</strong></div>
      <div><small>目标平台</small><strong>{{ project.targetPlatform }}</strong></div>
      <div><small>项目状态</small><strong>{{ project.status || "已创建" }}</strong></div>
    </div>
    <div class="empty-state">项目工作台已准备就绪。下一步可在这里开始游戏生成。</div>
  </section>
</template>

<script setup>
defineProps({ project: { type: Object, required: true } });
defineEmits(["back"]);
</script>
