<template>
  <FeaturePageLayout :title="artifact?.title || 'Artifact 详情'" eyebrow="项目制品" description="内容以纯文本方式展示，不执行其中的 HTML、脚本或提示词。">
    <template #actions><RouterLink class="button ghost" :to="`/projects/${projectUuid}/artifacts`">返回总览</RouterLink></template>
    <AsyncState v-if="loading" kind="loading" /><p v-else-if="error" class="alert danger" role="alert">{{ error }}</p>
    <template v-else-if="artifact"><dl class="artifact-facts"><div><dt>UUID</dt><dd>{{ artifact.artifactUuid }}</dd></div><div><dt>类型</dt><dd>{{ artifact.artifactType }}</dd></div><div><dt>Digest</dt><dd>{{ artifact.contentDigest }}</dd></div><div><dt>Runtime</dt><dd>{{ artifact.runtimeEligible ? '可用' : '不可用' }}</dd></div></dl><pre class="artifact-content">{{ artifact.content }}</pre></template>
  </FeaturePageLayout>
</template>
<script setup>
import { computed,onMounted,ref } from "vue";import { useRoute } from "vue-router";import { artifactsApi } from "../../shared/api/artifacts.js";import AsyncState from "../../shared/ui/AsyncState.vue";import FeaturePageLayout from "../../shared/ui/FeaturePageLayout.vue";
const route=useRoute();const projectUuid=computed(()=>String(route.params.projectUuid));const artifact=ref(null),loading=ref(true),error=ref("");onMounted(async()=>{try{artifact.value=await artifactsApi.detail(projectUuid.value,String(route.params.artifactUuid));}catch(cause){error.value=cause.status===403?"没有权限查看此项目的 Artifact":cause.message||"无法读取 Artifact";}finally{loading.value=false;}});
</script>
