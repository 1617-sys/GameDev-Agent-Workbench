<template>
  <section class="async-state" :aria-busy="copy.busy" :role="kind === 'forbidden' || kind === 'network-error' ? 'alert' : 'status'">
    <LoaderCircle v-if="copy.busy" class="spin" :size="22" />
    <CircleAlert v-else-if="kind === 'forbidden' || kind === 'network-error' || kind === 'partial'" :size="22" />
    <div><strong>{{ title || copy.title }}</strong><p>{{ message || copy.message }}</p></div>
    <button v-if="retryable" class="button secondary" type="button" @click="$emit('retry')">重试</button>
  </section>
</template>

<script setup>
import { computed } from "vue";
import { CircleAlert, LoaderCircle } from "@lucide/vue";
import { asyncStateCopy } from "./safeStates";
const props = defineProps({ kind: { type: String, required: true }, title: String, message: String, retryable: Boolean });
defineEmits(["retry"]);
const copy = computed(() => asyncStateCopy(props.kind));
</script>
