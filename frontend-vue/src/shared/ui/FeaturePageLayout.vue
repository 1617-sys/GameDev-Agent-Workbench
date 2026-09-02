<template>
  <section class="feature-page page-stack">
    <header class="page-heading with-action">
      <div><span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span><h1>{{ title }}</h1><p v-if="description">{{ description }}</p></div>
      <div v-if="$slots.actions" class="feature-actions"><slot name="actions" /></div>
    </header>
    <slot />
  </section>
</template>

<script setup>
defineProps({ title: { type: String, required: true }, eyebrow: String, description: String });
</script>
