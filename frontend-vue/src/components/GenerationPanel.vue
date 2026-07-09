<template>
  <section class="generation-panel">
    <div class="section-heading">
      <span>输入游戏想法</span>
      <small>AI 多 Agent 生成</small>
    </div>

    <label>
      标题
      <input v-model="localValue.title" placeholder="例如：像素地牢探索 Demo" />
    </label>
    <label>
      游戏想法
      <textarea v-model="localValue.idea" rows="6" placeholder="描述你想生成的轻量 Web 小游戏"></textarea>
    </label>
    <label>
      补充上下文
      <textarea v-model="localValue.context" rows="4" placeholder="目标平台、风格、限制、玩法偏好"></textarea>
    </label>

    <div class="button-row">
      <button class="primary-button" :disabled="disabled" @click="$emit('generate')">
        {{ running ? "生成中..." : "一键生成试玩 Demo" }}
      </button>
      <button class="secondary-button" :disabled="running" @click="$emit('fill-example')">填入示例</button>
    </div>

    <p v-if="!activeProjectUuid" class="hint error">请先创建或选择一个项目。</p>
    <p v-else class="hint">流程：游戏想法 → 多 Agent → GameConfig → Phaser3 Runtime → 可试玩 Demo。</p>
  </section>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  modelValue: {
    type: Object,
    required: true
  },
  running: {
    type: Boolean,
    default: false
  },
  activeProjectUuid: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["update:modelValue", "generate", "fill-example"]);

const localValue = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const disabled = computed(() =>
  props.running || !props.activeProjectUuid || !props.modelValue.title || !props.modelValue.idea
);
</script>
