<template>
  <section class="summary-card">
    <div class="section-heading">
      <span>生成结果摘要</span>
      <small :class="['status-pill', status.toLowerCase()]">{{ status }}</small>
    </div>
    <h3>{{ title }}</h3>
    <p>{{ summary }}</p>
    <div class="summary-grid">
      <div>
        <strong>{{ artifactCount }}</strong>
        <span>产物</span>
      </div>
      <div>
        <strong>{{ runCount }}</strong>
        <span>运行记录</span>
      </div>
      <div>
        <strong>{{ configValid ? "通过" : "待校验" }}</strong>
        <span>GameConfig</span>
      </div>
    </div>
    <div class="button-row">
      <button class="secondary-button" :disabled="!demoUrl" @click="$emit('open-demo')">打开试玩页</button>
      <button class="ghost-button" @click="$emit('switch-tab', 'debug')">调试信息</button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: "等待生成"
  },
  summary: {
    type: String,
    default: "输入游戏想法并点击生成后，这里会展示产物摘要。"
  },
  status: {
    type: String,
    default: "READY"
  },
  artifactCount: {
    type: Number,
    default: 0
  },
  runCount: {
    type: Number,
    default: 0
  },
  configValid: {
    type: Boolean,
    default: false
  },
  demoUrl: {
    type: String,
    default: ""
  }
});

defineEmits(["open-demo", "switch-tab"]);
</script>
