<template>
  <FeaturePageLayout title="Artifact 总览" eyebrow="项目证据" description="仅展示当前项目中后端持久化的制品。">
    <template #actions><button class="button ghost" type="button" :disabled="loading" @click="load">刷新</button></template>
    <p v-if="error" class="alert danger" role="alert">{{ error }}</p>
    <AsyncState v-if="loading" kind="loading" />
    <template v-else>
      <label class="artifact-filter">类型筛选<select v-model="type"><option value="">全部</option><option v-for="value in types" :key="value">{{ value }}</option></select></label>
      <AsyncState v-if="pageModel.total === 0" kind="empty" />
      <div v-else class="artifact-list">
        <RouterLink v-for="artifact in pageModel.items" :key="artifact.artifactUuid" :to="`/projects/${projectUuid}/artifacts/${artifact.artifactUuid}`">
          <div><strong>{{ artifact.title || artifact.artifactType }}</strong><small>{{ artifact.artifactType }} · {{ artifact.schemaKey || '无 schema' }} {{ artifact.schemaVersion || '' }}</small></div>
          <code>{{ artifact.artifactUuid }}</code><span>{{ artifact.runtimeEligible ? "Runtime 可用" : "仅证据" }}</span>
        </RouterLink>
      </div>
      <nav v-if="pageModel.totalPages > 1" class="pagination"><button :disabled="page===0" @click="page--">上一页</button><span>{{ page + 1 }} / {{ pageModel.totalPages }}</span><button :disabled="page+1>=pageModel.totalPages" @click="page++">下一页</button></nav>
    </template>
  </FeaturePageLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { artifactsApi, artifactPage } from "../../shared/api/artifacts.js";
import AsyncState from "../../shared/ui/AsyncState.vue";
import FeaturePageLayout from "../../shared/ui/FeaturePageLayout.vue";
const route=useRoute();const projectUuid=computed(()=>String(route.params.projectUuid));const items=ref([]),loading=ref(false),error=ref(""),type=ref(""),page=ref(0);
const types=computed(()=>[...new Set(items.value.map(item=>item.artifactType))].sort());const pageModel=computed(()=>artifactPage(items.value,{type:type.value,page:page.value,size:20}));watch(type,()=>{page.value=0;});onMounted(load);
async function load(){loading.value=true;error.value="";try{items.value=await artifactsApi.list(projectUuid.value);}catch(cause){error.value=cause.message||"无法读取 Artifact";}finally{loading.value=false;}}
</script>
