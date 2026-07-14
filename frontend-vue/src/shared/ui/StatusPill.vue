<template><span class="status-pill" :class="`tone-${meta.tone}`"><span></span>{{ label || meta.label }}</span></template>
<script setup>
import { computed } from "vue";
import { statusMeta } from "../presentation/workflow";
const props = defineProps({ status: { type: String, default: "" }, label: { type: String, default: "" } });
const meta = computed(() => statusMeta(props.status));
</script>
