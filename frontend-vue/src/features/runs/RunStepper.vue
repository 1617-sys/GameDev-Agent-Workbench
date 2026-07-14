<template>
  <ol class="run-stepper">
    <li v-for="(step, index) in normalizedSteps" :key="step.stepKey" :class="`step-${step.status?.toLowerCase() || 'pending'}`">
      <div class="step-marker"><Check v-if="step.status === 'SUCCESS'" :size="15" /><LoaderCircle v-else-if="step.status === 'RUNNING'" class="spin" :size="15" /><X v-else-if="step.status === 'FAILED'" :size="15" /><span v-else>{{ index + 1 }}</span></div>
      <div><strong>{{ stepLabel(step.stepKey) }}</strong><small>{{ statusMeta(step.status).label }}</small></div>
    </li>
  </ol>
</template>

<script setup>
import { computed } from "vue";
import { Check, LoaderCircle, X } from "@lucide/vue";
import { statusMeta, stepLabel } from "../../shared/presentation/workflow";
const props = defineProps({ steps: { type: Array, default: () => [] } });
const expected = ["game_concept", "core_loop_design", "task_breakdown", "game_config_generate"];
const normalizedSteps = computed(() => expected.map((key, index) => props.steps.find((step) => step.stepKey === key) || { stepKey: key, stepOrder: index + 1, status: "PENDING" }));
</script>
