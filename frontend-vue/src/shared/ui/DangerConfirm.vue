<template>
  <section class="danger-confirm" role="group" aria-labelledby="danger-confirm-title">
    <strong id="danger-confirm-title">危险操作确认</strong>
    <p>{{ state.message }}</p>
    <label>输入 <code>{{ expected }}</code> 继续<input v-model="input" :placeholder="expected" autocomplete="off" /></label>
    <button class="button danger" type="button" :disabled="state.disabled || busy" @click="$emit('confirm')">
      {{ busy ? "正在处理…" : actionLabel }}
    </button>
  </section>
</template>

<script setup>
import { computed, ref } from "vue";
import { dangerConfirmation } from "./safeStates";
const props = defineProps({ expected: { type: String, required: true }, actionLabel: { type: String, default: "确认执行" }, busy: Boolean });
defineEmits(["confirm"]);
const input = ref("");
const state = computed(() => dangerConfirmation(props.expected, input.value));
</script>
