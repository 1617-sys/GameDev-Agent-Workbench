<template>
  <main class="app-shell run-center-shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">AI GAME WORKFLOW</p>
        <h1>小游戏生成工作台</h1>
      </div>
      <button v-if="session.token" class="secondary-button" @click="logout">退出登录</button>
    </header>

    <section v-if="!session.token" class="login-card">
      <h2>登录后继续</h2>
      <p class="hint">认证凭据仅保留在当前会话的认证层，不写入浏览器存储。</p>
      <form class="login-grid" @submit.prevent="login">
        <input v-model="credentials.username" autocomplete="username" placeholder="用户名" required />
        <input v-model="credentials.password" autocomplete="current-password" type="password" placeholder="密码" required />
        <button class="primary-button" :disabled="authLoading">{{ authLoading ? "登录中…" : "登录" }}</button>
      </form>
      <p v-if="authError" class="error" role="alert">{{ authError }}</p>
    </section>

    <WorkflowRunView v-else-if="workflowRunUuid" :store="store" :evidence-api="workflowApi.getRagEvidence" :workflow-run-uuid="workflowRunUuid" @back="navigate('/')" />
    <KnowledgeLibraryView v-else-if="knowledgeProjectUuid" :api="knowledgeApi" :project-uuid="knowledgeProjectUuid" @back="navigate('/')" />
    <PromptMetricsView v-else-if="routePath === '/analytics/prompt-versions'" :api="analyticsApi.promptVersions" @back="navigate('/')" />
    <WorkbenchView v-else :api="workflowApi" @submitted="navigateToRun" @open-knowledge="navigateToKnowledge" />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { createAuthApi } from "./api/authApi";
import { createHttpClient } from "./api/httpClient";
import { createWorkflowApi } from "./api/workflowApi";
import { createKnowledgeApi } from "./api/knowledgeApi";
import { workflowRunUuidFromPath, navigateToWorkflowRun } from "./router/workflowRoute";
import { createWorkflowRunStore } from "./stores/workflowRunStore";
import WorkbenchView from "./views/WorkbenchView.vue";
import WorkflowRunView from "./views/WorkflowRunView.vue";
import PromptMetricsView from "./views/PromptMetricsView.vue";
import KnowledgeLibraryView from "./views/KnowledgeLibraryView.vue";
import { createAnalyticsApi } from "./api/analyticsApi";

const session = reactive({ token: "" });
const credentials = reactive({ username: "", password: "" });
const authLoading = ref(false);
const authError = ref("");
const routePath = ref(window.location.pathname);
const workflowRunUuid = computed(() => workflowRunUuidFromPath(routePath.value));
const knowledgeProjectUuid = computed(() => {
  const match = routePath.value.match(/^\/projects\/([^/]+)\/knowledge\/?$/);
  return match ? decodeURIComponent(match[1]) : null;
});
const http = createHttpClient({ getToken: () => session.token });
const workflowApi = createWorkflowApi(http);
const authApi = createAuthApi(http);
const analyticsApi = createAnalyticsApi(http);
const knowledgeApi = createKnowledgeApi(http);
const store = createWorkflowRunStore({ api: workflowApi });

function syncRoute() { routePath.value = window.location.pathname; }
function navigate(path) { window.history.pushState({}, "", path); syncRoute(); }
function navigateToRun(uuid) { navigateToWorkflowRun(uuid); syncRoute(); }
function navigateToKnowledge(projectUuid) { navigate(`/projects/${encodeURIComponent(projectUuid)}/knowledge`); }

async function login() {
  authLoading.value = true;
  authError.value = "";
  try {
    const response = await authApi.login(credentials);
    if (!response?.token) throw new Error("登录响应无效");
    session.token = response.token;
    credentials.password = "";
  } catch (error) {
    authError.value = error.message || "登录失败";
  } finally { authLoading.value = false; }
}

function logout() {
  session.token = "";
  store.disconnectAll();
  navigate("/");
}

onMounted(() => window.addEventListener("popstate", syncRoute));
onBeforeUnmount(() => {
  window.removeEventListener("popstate", syncRoute);
  store.disconnectAll();
});
</script>
