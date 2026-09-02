<template>
  <FeaturePageLayout title="项目知识库" eyebrow="RAG 来源" description="上传文件由后端嗅探类型、解析并建立索引；失败内容不会进入检索。">
    <template #actions><label v-if="store.capabilities.upload" class="button primary">{{ store.uploading ? "上传中…" : "上传文件" }}<input hidden type="file" accept=".pdf,.md,.markdown,.txt" :disabled="store.uploading" @change="upload" /></label></template>
    <p v-if="store.error" class="alert danger" role="alert">{{ store.error }}</p>
    <AsyncState v-if="store.loading && !store.documents.length" kind="loading" /><AsyncState v-else-if="!store.documents.length" kind="empty" message="知识库为空。上传项目规则、策划说明或设计约束后可供 RAG 检索。" />
    <div v-else class="knowledge-list"><article v-for="doc in store.documents" :key="doc.documentUuid"><div><strong>{{ doc.name }}</strong><small>{{ doc.sourceType }} · v{{ doc.version }} · {{ doc.contentHashSummary }}</small></div><span :class="['status-pill', `tone-${tone(doc.status)}`]">{{ doc.status }}</span><p v-if="doc.failureSummary" class="alert danger">{{ doc.failureSummary }}</p></article></div>
  </FeaturePageLayout>
</template>
<script setup>
import { computed,onBeforeUnmount,onMounted } from "vue";import { useRoute } from "vue-router";import { useKnowledgeStore } from "./knowledgeStore.js";import AsyncState from "../../shared/ui/AsyncState.vue";import FeaturePageLayout from "../../shared/ui/FeaturePageLayout.vue";
const route=useRoute(),store=useKnowledgeStore(),projectUuid=computed(()=>String(route.params.projectUuid));onMounted(async()=>{try{await store.load(projectUuid.value);store.poll(projectUuid.value);}catch{}});onBeforeUnmount(()=>store.stopPolling());async function upload(event){const file=event.target.files?.[0];if(!file)return;try{await store.upload(projectUuid.value,file);}catch{}finally{event.target.value="";}}function tone(status){return status==="READY"?"success":status==="FAILED"?"danger":"info";}
</script>
