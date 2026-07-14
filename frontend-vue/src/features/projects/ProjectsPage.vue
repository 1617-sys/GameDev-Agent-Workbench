<template>
  <div class="page-stack">
    <header class="page-heading with-action">
      <div><p class="overline">PROJECTS</p><h1>项目中心</h1><p>选择已有项目，或者创建一个新的游戏原型。</p></div>
      <button class="button primary" type="button" @click="dialogOpen = true"><Plus :size="18" />创建项目</button>
    </header>

    <p v-if="projects.error" class="alert danger" role="alert">{{ projects.error }}</p>
    <section v-if="projects.loading" class="empty-panel"><LoaderCircle class="spin" :size="24" /><p>正在加载项目…</p></section>
    <section v-else-if="projects.items.length === 0" class="empty-panel">
      <FolderPlus :size="32" /><h2>创建第一个游戏项目</h2><p>项目会保存你的创意、运行记录和可玩 Demo。</p>
      <button class="button primary" type="button" @click="dialogOpen = true">创建项目</button>
    </section>
    <section v-else class="project-table" aria-label="项目列表">
      <div class="table-head"><span>项目</span><span>类型</span><span>状态</span><span>更新时间</span><span></span></div>
      <article v-for="project in projects.items" :key="project.projectUuid" class="project-row">
        <div class="project-name"><span class="project-icon"><Gamepad2 :size="19" /></span><div><strong>{{ project.name }}</strong><small>{{ project.description }}</small></div></div>
        <span>{{ project.gameType }}</span>
        <span><StatusPill :status="project.status || 'READY'" :label="project.status || '可创作'" /></span>
        <time>{{ formatDate(project.updatedAt || project.createdAt) }}</time>
        <RouterLink class="button ghost" :to="`/projects/${project.projectUuid}/studio`">进入创作台<ArrowRight :size="16" /></RouterLink>
      </article>
    </section>

    <div v-if="dialogOpen" class="modal-backdrop" @click.self="closeDialog">
      <section class="modal" role="dialog" aria-modal="true" aria-labelledby="create-project-title">
        <header><div><p class="overline">NEW PROJECT</p><h2 id="create-project-title">创建游戏项目</h2></div><button class="icon-button" type="button" title="关闭" @click="closeDialog"><X :size="19" /></button></header>
        <form class="form-stack" @submit.prevent="createProject">
          <label><span>项目名称</span><input v-model.trim="form.name" required maxlength="80" placeholder="例如：星际拾荒者" /></label>
          <label><span>项目描述</span><textarea v-model.trim="form.description" required rows="4" placeholder="简单描述玩法目标和主题。"></textarea></label>
          <div class="form-grid">
            <label><span>游戏类型</span><select v-model="form.gameType"><option value="top_down_collect">俯视角收集</option></select></label>
            <label><span>目标平台</span><select v-model="form.targetPlatform"><option value="web">Web / H5</option></select></label>
          </div>
          <p v-if="projects.error" class="alert danger">{{ projects.error }}</p>
          <footer class="modal-actions"><button class="button ghost" type="button" @click="closeDialog">取消</button><button class="button primary" :disabled="projects.creating">{{ projects.creating ? "创建中…" : "创建并进入" }}</button></footer>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowRight, FolderPlus, Gamepad2, LoaderCircle, Plus, X } from "@lucide/vue";
import StatusPill from "../../shared/ui/StatusPill.vue";
import { useProjectsStore } from "./projectsStore";

const router = useRouter();
const projects = useProjectsStore();
const dialogOpen = ref(false);
const form = reactive({ name: "", description: "", gameType: "top_down_collect", targetPlatform: "web" });

onMounted(() => projects.load());
const formatDate = (value) => value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "--";
function closeDialog() { if (!projects.creating) dialogOpen.value = false; }
async function createProject() {
  try {
    const project = await projects.create({ ...form });
    dialogOpen.value = false;
    await router.push(`/projects/${project.projectUuid}/studio`);
  } catch { /* rendered by store */ }
}
</script>
