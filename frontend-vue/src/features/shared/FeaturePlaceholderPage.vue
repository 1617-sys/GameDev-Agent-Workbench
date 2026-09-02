<template>
  <FeaturePageLayout :title="route.meta.title || '功能页面'" eyebrow="能力已授权" description="该入口由后端 capability 声明控制。">
    <AsyncState kind="partial" message="页面契约已建立，领域数据适配器正在按 OpenSpec 任务顺序接入。" />
  </FeaturePageLayout>
</template>

<script setup>
import { useRoute } from "vue-router";
import AsyncState from "../../shared/ui/AsyncState.vue";
import FeaturePageLayout from "../../shared/ui/FeaturePageLayout.vue";
const route = useRoute();
</script>
