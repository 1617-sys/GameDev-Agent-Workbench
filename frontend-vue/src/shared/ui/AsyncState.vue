<template>
  <section v-if="showState" class="async-state" :aria-busy="copy.busy" :role="resolvedKind === 'forbidden' || resolvedKind === 'network-error' ? 'alert' : 'status'">
    <LoaderCircle v-if="copy.busy" class="spin" :size="22" />
    <CircleAlert v-else-if="kind === 'forbidden' || kind === 'network-error' || kind === 'partial'" :size="22" />
    <div><strong>{{ title || copy.title }}</strong><p>{{ message || error || emptyText || copy.message }}</p></div>
    <button v-if="retryable" class="button secondary" type="button" @click="$emit('retry')">重试</button>
  </section><slot v-else />
</template>

<script setup>
import { computed } from "vue";
import { CircleAlert, LoaderCircle } from "@lucide/vue";
import { asyncStateCopy, asyncStateKind } from "./safeStates";
const props = defineProps({ kind: { type: String, default: "" }, loading: Boolean, error: { type: String, default: "" }, empty: Boolean, emptyText: String, title: String, message: String, retryable: Boolean });
defineEmits(["retry"]);
const resolvedKind = computed(() => props.kind || asyncStateKind(props));
const showState = computed(() => Boolean(props.kind || props.loading || props.error || props.empty));
const copy = computed(() => asyncStateCopy(resolvedKind.value));
</script>
