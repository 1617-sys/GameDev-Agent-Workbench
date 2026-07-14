<template>
  <main class="app-shell run-center-shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">AI GAME WORKFLOW</p>
        <h1>小游戏生成工作台</h1>
      </div>
      <div v-if="session.token" class="topbar-actions">
        <span v-if="session.user" class="signed-in-user">{{ session.user.username }}</span>
        <button class="secondary-button" type="button" @click="logout">退出登录</button>
      </div>
    </header>

    <section v-if="sessionChecking" class="login-card" aria-busy="true">
      <h2>正在确认登录状态…</h2>
      <p class="hint">正在恢复当前浏览器会话。</p>
    </section>

    <section v-else-if="!session.token" class="login-card">
      <div class="auth-tabs" role="tablist" aria-label="账号操作">
        <button class="auth-tab" :class="{ active: authMode === 'login' }" type="button" role="tab" :aria-selected="authMode === 'login'" @click="selectAuthMode('login')">登录</button>
        <button class="auth-tab" :class="{ active: authMode === 'register' }" type="button" role="tab" :aria-selected="authMode === 'register'" @click="selectAuthMode('register')">注册账号</button>
      </div>
      <h2>{{ isRegisterMode ? "创建业务账号" : "登录后继续" }}</h2>
      <p class="hint">{{ isRegisterMode ? "请使用业务账号，不要使用 MySQL 或 Docker 的连接账号。" : "登录状态仅保留在当前浏览器会话中，不会写入 localStorage。" }}</p>
      <form class="login-grid" @submit.prevent="submitAuth">
        <input v-model.trim="credentials.username" autocomplete="username" :placeholder="isRegisterMode ? '用户名（4-20 个字符）' : '用户名'" :minlength="isRegisterMode ? 4 : undefined" :maxlength="isRegisterMode ? 20 : undefined" required />
        <input v-model="credentials.password" :autocomplete="isRegisterMode ? 'new-password' : 'current-password'" type="password" :placeholder="isRegisterMode ? '密码（6-32 个字符）' : '密码'" :minlength="isRegisterMode ? 6 : undefined" :maxlength="isRegisterMode ? 32 : undefined" required />
        <input v-if="isRegisterMode" v-model="confirmPassword" autocomplete="new-password" type="password" placeholder="再次输入密码" minlength="6" maxlength="32" required />
        <button class="primary-button" :disabled="authLoading">{{ authLoading ? (isRegisterMode ? "注册中…" : "登录中…") : (isRegisterMode ? "注册并登录" : "登录") }}</button>
      </form>
      <p v-if="authError" class="error" role="alert">{{ authError }}</p>
    </section>

    <WorkflowRunView v-else-if="workflowRunUuid" :store="store" :artifact-api="workflowApi.getArtifact" :workflow-run-uuid="workflowRunUuid" @back="returnToWorkspace" />
    <KnowledgeLibraryView v-else-if="knowledgeProjectUuid" :api="knowledgeApi" :project-uuid="knowledgeProjectUuid" @back="returnToWorkspace" />
    <WorkbenchView v-else-if="selectedProject" :api="workflowApi" :project="selectedProject" @back="returnToProjects" @submitted="navigateToRun" @open-knowledge="navigateToKnowledge" />
    <ProjectListView v-else :api="projectApi" @selected="selectProject" />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { createAuthApi } from "./api/authApi";
import { createHttpClient } from "./api/httpClient";
import { createProjectApi } from "./api/projectApi";
import { createWorkflowApi } from "./api/workflowApi";
import { createKnowledgeApi } from "./api/knowledgeApi";
import { navigateToWorkflowRun, workflowRunUuidFromPath } from "./router/workflowRoute";
import { createWorkflowRunStore } from "./stores/workflowRunStore";
import ProjectListView from "./views/ProjectListView.vue";
import WorkbenchView from "./views/WorkbenchView.vue";
import WorkflowRunView from "./views/WorkflowRunView.vue";
import KnowledgeLibraryView from "./views/KnowledgeLibraryView.vue";

const SESSION_TOKEN_KEY = "gamedev-agent-workbench.session-token";
const session = reactive({ token: "", user: null });
const credentials = reactive({ username: "", password: "" });
const authMode = ref("login");
const confirmPassword = ref("");
const authLoading = ref(false);
const authError = ref("");
const sessionChecking = ref(true);
const selectedProject = ref(null);
const routePath = ref(window.location.pathname);
const isRegisterMode = computed(() => authMode.value === "register");
const workflowRunUuid = computed(() => workflowRunUuidFromPath(routePath.value));
const knowledgeProjectUuid = computed(() => {
  const match = routePath.value.match(/^\/projects\/([^/]+)\/knowledge\/?$/);
  return match ? decodeURIComponent(match[1]) : null;
});

const http = createHttpClient({ getToken: () => session.token, onUnauthorized: clearAuthentication });
const authApi = createAuthApi(http);
const projectApi = createProjectApi(http);
const workflowApi = createWorkflowApi(http);
const knowledgeApi = createKnowledgeApi(http);
const store = createWorkflowRunStore({ api: workflowApi });

function syncRoute() { routePath.value = window.location.pathname; }
function navigate(path) { window.history.pushState({}, "", path); syncRoute(); }
function navigateToRun(workflowRunUuid) { navigateToWorkflowRun(workflowRunUuid); syncRoute(); }
function navigateToKnowledge(projectUuid) { navigate(`/projects/${encodeURIComponent(projectUuid)}/knowledge`); }
function returnToWorkspace() { navigate("/"); }
function returnToProjects() { selectedProject.value = null; navigate("/"); }

function readSessionToken() {
  try { return window.sessionStorage.getItem(SESSION_TOKEN_KEY) || ""; } catch { return ""; }
}

function saveSessionToken(token) {
  try { window.sessionStorage.setItem(SESSION_TOKEN_KEY, token); } catch { /* Session recovery is unavailable when storage is disabled. */ }
}

function clearAuthentication() {
  session.token = "";
  session.user = null;
  selectedProject.value = null;
  credentials.password = "";
  confirmPassword.value = "";
  store.disconnectAll();
  try { window.sessionStorage.removeItem(SESSION_TOKEN_KEY); } catch { /* Storage is unavailable. */ }
}

function selectAuthMode(mode) {
  authMode.value = mode;
  authError.value = "";
  confirmPassword.value = "";
}

function validateCredentials(request) {
  if (!request.username || !request.password) return "请输入用户名和密码。";
  if (!isRegisterMode.value) return "";
  if (request.username.length < 4 || request.username.length > 20) return "用户名长度需为 4-20 个字符。";
  if (request.password.length < 6 || request.password.length > 32) return "密码长度需为 6-32 个字符。";
  if (request.password !== confirmPassword.value) return "两次输入的密码不一致。";
  return "";
}

async function submitAuth() {
  if (authLoading.value) return;
  const request = { username: credentials.username.trim(), password: credentials.password };
  const validationError = validateCredentials(request);
  if (validationError) {
    authError.value = validationError;
    return;
  }
  authLoading.value = true;
  authError.value = "";
  try {
    if (isRegisterMode.value) await authApi.register(request);
    const response = await authApi.login(request);
    if (!response?.token) throw new Error("登录响应无效");
    session.token = response.token;
    session.user = response.user || null;
    saveSessionToken(response.token);
    credentials.password = "";
    confirmPassword.value = "";
  } catch (error) {
    authError.value = error.message || (isRegisterMode.value ? "注册或登录失败" : "登录失败");
  } finally {
    authLoading.value = false;
  }
}

async function restoreSession() {
  const token = readSessionToken();
  if (!token) {
    sessionChecking.value = false;
    return;
  }
  session.token = token;
  try {
    session.user = await authApi.me();
  } catch (error) {
    clearAuthentication();
    if (error.status !== 401) authError.value = "无法恢复登录状态，请重新登录。";
  } finally {
    sessionChecking.value = false;
  }
}

function selectProject(project) {
  selectedProject.value = project;
}

function logout() {
  clearAuthentication();
  authError.value = "";
  navigate("/");
}

onMounted(() => {
  window.addEventListener("popstate", syncRoute);
  restoreSession();
});
onBeforeUnmount(() => {
  window.removeEventListener("popstate", syncRoute);
  store.disconnectAll();
});
</script>
