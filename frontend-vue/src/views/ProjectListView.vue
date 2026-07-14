<template>
  <section class="project-lifecycle-view">
    <div class="section-heading">
      <div>
        <span>我的项目</span>
        <p class="hint">创建一个项目，或继续已有项目。项目标识由系统管理，无需手动填写。</p>
      </div>
      <button class="secondary-button" type="button" :disabled="busy" @click="loadProjects">刷新列表</button>
    </div>

    <section class="project-panel">
      <h2>创建项目</h2>
      <form class="project-form" @submit.prevent="createProject">
        <label>项目名称<input v-model.trim="form.name" required placeholder="例如：星际拾荒者" /></label>
        <label>游戏类型<input v-model.trim="form.gameType" required placeholder="例如：top_down_collect" /></label>
        <label>目标平台<input v-model.trim="form.targetPlatform" required placeholder="例如：web" /></label>
        <label>项目描述<textarea v-model.trim="form.description" required rows="4" placeholder="简要描述你想制作的游戏。" /></label>
        <button class="primary-button" :disabled="busy">{{ creating ? "创建中…" : "创建项目" }}</button>
      </form>
      <p v-if="createError" class="error" role="alert">{{ createError }}</p>
    </section>

    <section class="project-panel">
      <div class="section-heading">
        <div><span>项目列表</span></div>
        <small v-if="!loading">{{ projects.length }} 个项目</small>
      </div>
      <p v-if="loading" class="empty-state" role="status">正在加载项目…</p>
      <div v-else-if="loadError" class="empty-state error" role="alert">
        <p>{{ loadError }}</p>
        <button class="secondary-button" type="button" :disabled="busy" @click="loadProjects">重新加载</button>
      </div>
      <div v-else-if="projects.length === 0" class="empty-state">还没有项目。请先创建你的第一个游戏项目。</div>
      <div v-else class="project-list">
        <button v-for="project in projects" :key="project.projectUuid" class="project-item" type="button" :disabled="busy" @click="selectProject(project)">
          <strong>{{ project.name }}</strong>
          <span>{{ project.gameType }} · {{ project.targetPlatform }}</span>
          <span>{{ selecting === project.projectUuid ? "正在进入…" : project.description }}</span>
        </button>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";

const props = defineProps({ api: { type: Object, required: true } });
const emit = defineEmits(["selected"]);
const projects = ref([]);
const loading = ref(true);
const loadError = ref("");
const creating = ref(false);
const selecting = ref("");
const createError = ref("");
const form = reactive({ name: "", gameType: "", targetPlatform: "", description: "" });
const busy = computed(() => loading.value || creating.value || Boolean(selecting.value));

async function loadProjects() {
  loading.value = true;
  loadError.value = "";
  try {
    const result = await props.api.list();
    projects.value = Array.isArray(result) ? result : [];
  } catch (error) {
    projects.value = [];
    loadError.value = error.message || "项目列表加载失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}

async function createProject() {
  if (creating.value) return;
  creating.value = true;
  createError.value = "";
  try {
    const project = await props.api.create({ ...form });
    if (!project?.projectUuid) throw new Error("创建项目响应无效");
    projects.value = [project, ...projects.value.filter((item) => item.projectUuid !== project.projectUuid)];
    emit("selected", project);
  } catch (error) {
    createError.value = error.message || "创建项目失败，请稍后重试。";
  } finally {
    creating.value = false;
  }
}

async function selectProject(project) {
  if (selecting.value) return;
  selecting.value = project.projectUuid;
  try {
    const current = await props.api.get(project.projectUuid);
    if (!current?.projectUuid) throw new Error("项目详情响应无效");
    emit("selected", current);
  } catch (error) {
    loadError.value = error.message || "无法打开该项目，请稍后重试。";
  } finally {
    selecting.value = "";
  }
}

onMounted(loadProjects);
</script>
