<template>
  <section class="json-viewer">
    <button class="ghost-button" type="button" @click="open = !open">
      {{ open ? "隐藏 JSON" : label }}
    </button>
    <button class="ghost-button" type="button" :disabled="!value" @click="copyJson">复制</button>
    <pre v-if="open" class="json-block">{{ formatted }}</pre>
  </section>
</template>

<script setup>
import { computed, ref } from "vue";

const props = defineProps({
  value: {
    type: [Object, Array, String, Number, Boolean, null],
    default: null
  },
  label: {
    type: String,
    default: "查看 JSON"
  },
  defaultOpen: {
    type: Boolean,
    default: false
  }
});

const open = ref(props.defaultOpen);

const formatted = computed(() => {
  if (props.value === null || props.value === undefined || props.value === "") {
    return "暂无调试数据";
  }
  if (typeof props.value === "string") {
    try {
      return JSON.stringify(JSON.parse(props.value), null, 2);
    } catch {
      return props.value;
    }
  }
  return JSON.stringify(props.value, null, 2);
});

async function copyJson() {
  await navigator.clipboard.writeText(formatted.value);
}
</script>
