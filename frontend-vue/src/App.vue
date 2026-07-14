<template>
  <RouterView v-if="route.meta.public" />
  <AppShell v-else>
    <RouterView />
  </AppShell>
</template>

<script setup>
import { useRoute } from "vue-router";
import AppShell from "./app/AppShell.vue";

const route = useRoute();
</script>
