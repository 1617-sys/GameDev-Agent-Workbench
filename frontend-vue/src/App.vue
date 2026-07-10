<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">GA</div>
        <div>
          <strong>AI Game Workflow</strong>
          <span>小游戏生成工作台</span>
        </div>
      </div>
      <nav>
        <button :class="{ active: activeView === 'workspace' }" @click="activeView = 'workspace'">工作台</button>
        <button :class="{ active: activeView === 'projects' }" @click="activeView = 'projects'">项目</button>
        <button :class="{ active: activeView === 'templates' }" @click="activeView = 'templates'">模板</button>
        <button :class="{ active: activeView === 'runs' }" @click="activeView = 'runs'; activeTab = 'runs'">运行记录</button>
        <button :class="{ active: activeView === 'artifacts' }" @click="activeView = 'artifacts'; activeTab = 'artifacts'">产物库</button>
      </nav>
    </aside>

    <main class="main-shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">AI Web 小游戏生成工作台</p>
          <h1>输入想法，生成可试玩 Demo</h1>
          <p>{{ activeProject?.name || "未选择项目" }} / {{ activeProjectUuid || "no-project" }}</p>
        </div>
        <div class="topbar-actions">
          <input v-model="state.baseUrl" class="base-url-input" />
          <button class="secondary-button" @click="saveBaseUrl">保存</button>
          <button class="secondary-button" @click="refreshAll">刷新</button>
          <button class="primary-button" @click="logout">退出</button>
        </div>
      </header>

      <section v-if="!state.token" class="login-card">
        <h2>先登录后开始生成</h2>
        <p>登录成功后 Token 会保存在浏览器本地，用于访问后端接口。</p>
        <div class="login-grid">
          <input v-model="state.auth.username" placeholder="用户名" />
          <input v-model="state.auth.password" type="password" placeholder="密码" />
          <button class="primary-button" @click="login">登录</button>
          <button class="secondary-button" @click="register">注册</button>
        </div>
        <p v-if="state.message" class="hint error">{{ state.message }}</p>
      </section>

      <template v-else>
        <section class="workspace-grid">
          <section class="project-panel">
            <div class="section-heading">
              <span>项目</span>
              <small>{{ state.projects.length }} 个</small>
            </div>
            <div class="project-form">
              <input v-model="state.projectForm.name" placeholder="项目名" />
              <input v-model="state.projectForm.gameType" placeholder="类型，例如 RPG" />
              <input v-model="state.projectForm.targetPlatform" placeholder="平台，例如 Web / PC" />
              <textarea v-model="state.projectForm.description" rows="3" placeholder="项目描述"></textarea>
              <button class="secondary-button" @click="createProject">新建项目</button>
            </div>
            <div class="project-list">
              <button
                v-for="project in state.projects"
                :key="project.projectUuid"
                :class="['project-item', { active: project.projectUuid === activeProjectUuid }]"
                @click="selectProject(project.projectUuid)"
              >
                <strong>{{ project.name }}</strong>
                <span>{{ project.gameType }} / {{ project.targetPlatform }} / {{ project.status }}</span>
              </button>
            </div>
          </section>

          <div class="center-column">
            <GenerationPanel
              v-model="state.generation"
              :running="state.streamRunning"
              :active-project-uuid="activeProjectUuid"
              @generate="runDemoWorkflow"
              @fill-example="fillExample"
            />

            <ResultSummaryCard
              :title="summaryTitle"
              :summary="summaryText"
              :status="workflowStatus"
              :artifact-count="state.artifacts.length"
              :run-count="state.runs.length"
              :config-valid="state.configValidation.valid"
              :demo-url="state.demoUrl"
              @open-demo="openDemo"
              @switch-tab="activeTab = $event"
            />
          </div>

          <WorkflowStepper
            :events="state.events"
            :config-valid="state.configValidation.valid"
            :demo-ready="Boolean(state.demoUrl)"
          />
        </section>

        <section class="tabs">
          <button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
            {{ tab.label }}
          </button>
        </section>

        <PhaserGamePreview
          v-if="activeTab === 'demo'"
          :game-config="state.gameConfig"
          :demo-url="state.demoUrl"
          @open-demo="openDemo"
          @regenerate-config="runSingleGameConfig"
        />

        <DocumentPreview v-if="activeTab === 'documents'" :docs="docs" />
        <ArtifactLibrary v-if="activeTab === 'artifacts'" :artifacts="state.artifacts" />

        <section v-if="activeTab === 'runs'" class="content-section">
          <div class="section-heading">
            <span>运行记录</span>
            <small>按项目、类型、状态筛选</small>
          </div>
          <div class="filter-row">
            <select v-model="state.runFilters.agentType" @change="loadRuns">
              <option value="">全部类型</option>
              <option v-for="type in agentTypes" :key="type" :value="type">{{ type }}</option>
            </select>
            <select v-model="state.runFilters.status" @change="loadRuns">
              <option value="">全部状态</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILED">FAILED</option>
              <option value="RUNNING">RUNNING</option>
            </select>
            <select v-model="state.runFilters.pageSize" @change="loadRuns">
              <option :value="5">每页 5 条</option>
              <option :value="10">每页 10 条</option>
              <option :value="20">每页 20 条</option>
            </select>
          </div>
          <div v-if="state.runs.length === 0" class="empty-state">暂无运行记录</div>
          <article v-for="run in state.runs" :key="run.runUuid" class="run-row">
            <strong>{{ run.agentType }}</strong>
            <span>{{ run.status }}</span>
            <span>{{ run.timeTakenMs || 0 }} ms</span>
            <span>{{ run.createdAt }}</span>
          </article>
        </section>

        <section v-if="activeTab === 'templates'" class="content-section">
          <div class="section-heading">
            <span>Prompt 模板</span>
            <small>当前保留后端能力，列表接口后续补齐</small>
          </div>
          <p class="hint">这里用于管理 AgentType 对应的 ACTIVE 模板。当前页面先保留入口，创建和修改建议继续用 Apifox 或后端接口调试。</p>
          <button class="secondary-button" disabled title="当前后端暂未提供模板分页列表接口">模板列表待开放</button>
        </section>

        <DebugPanel
          v-if="activeTab === 'debug'"
          :events="state.events"
          :raw-output="state.rawOutput"
          :game-config="state.gameConfig"
        />
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import ArtifactLibrary from "./components/ArtifactLibrary.vue";
import DebugPanel from "./components/DebugPanel.vue";
import DocumentPreview from "./components/DocumentPreview.vue";
import GenerationPanel from "./components/GenerationPanel.vue";
import PhaserGamePreview from "./components/PhaserGamePreview.vue";
import ResultSummaryCard from "./components/ResultSummaryCard.vue";
import WorkflowStepper from "./components/WorkflowStepper.vue";
import { artifactText, defaultGameConfig, extractGameConfig, extractGameConfigFromArtifacts, validateGameConfig } from "./game/gameConfig";

const tabs = [
  { key: "demo", label: "试玩 Demo" },
  { key: "documents", label: "设计文档" },
  { key: "artifacts", label: "产物库" },
  { key: "runs", label: "运行记录" },
  { key: "debug", label: "调试信息" }
];

const agentTypes = ["GAME_CONCEPT", "TASK_BREAKDOWN", "CORE_LOOP_DESIGN", "GAME_CONFIG_GENERATE"];

const activeView = ref("workspace");
const activeTab = ref("demo");

const state = reactive({
  baseUrl: localStorage.getItem("gaw.baseUrl") || "http://localhost:8080",
  token: localStorage.getItem("gaw.token") || "",
  auth: {
    username: localStorage.getItem("gaw.username") || "vandick",
    password: ""
  },
  projects: [],
  activeProjectUuid: localStorage.getItem("gaw.activeProjectUuid") || "",
  projectForm: {
    name: "像素地牢 Demo",
    gameType: "RPG",
    targetPlatform: "Web",
    description: "一个由 AI 工作流生成的最小可玩小游戏原型。"
  },
  generation: {
    title: "像素地牢探索 Demo",
    idea: "做一个像素风地牢探索游戏，玩家收集宝石并到达出口。",
    context: "目标平台是浏览器，优先做成能快速演示的 MVP。"
  },
  streamRunning: false,
  message: "",
  events: [],
  artifacts: [],
  runs: [],
  rawOutput: null,
  demoUrl: "",
  gameConfig: defaultGameConfig,
  configValidation: validateGameConfig(defaultGameConfig),
  runFilters: {
    pageNum: 1,
    pageSize: 10,
    agentType: "",
    status: ""
  }
});

const activeProjectUuid = computed(() => state.activeProjectUuid);
const activeProject = computed(() => state.projects.find((project) => project.projectUuid === state.activeProjectUuid));

const workflowStatus = computed(() => {
  const failed = state.events.find((event) => event.status === "FAILED");
  if (failed) return "FAILED";
  if (state.demoUrl) return "SUCCESS";
  if (state.streamRunning) return "RUNNING";
  return "READY";
});

const summaryTitle = computed(() => activeProject.value?.name || state.generation.title || "等待生成");
const summaryText = computed(() => {
  if (workflowStatus.value === "SUCCESS") {
    return "AI 已完成设计产物、GameConfig 校验和 Phaser3 Demo 构建，可以立即试玩。";
  }
  if (workflowStatus.value === "FAILED") {
    return latestEvent()?.message || "生成失败，请查看右侧步骤或调试信息。";
  }
  return "填写游戏想法并点击生成，系统会通过多 Agent 产出文档、配置和可试玩 Demo。";
});

const docs = computed(() => {
  const map = {
    GAME_CONCEPT_RESULT: "游戏概念",
    CORE_LOOP_DESIGN_RESULT: "核心循环",
    TASK_BREAKDOWN_RESULT: "任务拆解"
  };
  return Object.entries(map).map(([key, title]) => {
    const artifact = state.artifacts.find((item) => item.artifactType === key);
    return {
      key,
      title,
      content: artifact ? artifactText(artifact) : ""
    };
  });
});

onMounted(async () => {
  if (state.token) {
    await refreshAll();
  }
});

function authHeaders() {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${state.token}`
  };
}

async function api(path, options = {}) {
  const response = await fetch(`${state.baseUrl}${path}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
      ...(options.auth === false ? { "Content-Type": "application/json" } : authHeaders())
    }
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok || (data && data.code !== 0)) {
    throw new Error(data?.message || `HTTP ${response.status}`);
  }
  return data?.data ?? data;
}

async function login() {
  state.message = "";
  const data = await api("/api/auth/login", {
    method: "POST",
    auth: false,
    body: JSON.stringify(state.auth)
  });
  state.token = data.token;
  localStorage.setItem("gaw.token", state.token);
  localStorage.setItem("gaw.username", state.auth.username);
  await refreshAll();
}

async function register() {
  state.message = "";
  try {
    await api("/api/auth/register", {
      method: "POST",
      auth: false,
      body: JSON.stringify(state.auth)
    });
    await login();
  } catch (error) {
    state.message = error.message;
  }
}

function logout() {
  state.token = "";
  localStorage.removeItem("gaw.token");
}

function saveBaseUrl() {
  localStorage.setItem("gaw.baseUrl", state.baseUrl);
  state.message = "后端地址已保存";
}

async function refreshAll() {
  await loadProjects();
  if (state.activeProjectUuid) {
    await Promise.all([loadArtifacts(), loadRuns()]);
    updateGameConfigFromArtifacts();
  }
}

async function loadProjects() {
  state.projects = await api("/api/projects");
  if (!state.activeProjectUuid && state.projects.length > 0) {
    selectProject(state.projects[0].projectUuid);
  }
}

async function createProject() {
  const project = await api("/api/projects", {
    method: "POST",
    body: JSON.stringify(state.projectForm)
  });
  state.projects.unshift(project);
  selectProject(project.projectUuid);
}

async function selectProject(projectUuid) {
  state.activeProjectUuid = projectUuid;
  localStorage.setItem("gaw.activeProjectUuid", projectUuid);
  await Promise.all([loadArtifacts(), loadRuns()]);
  updateGameConfigFromArtifacts();
}

async function loadArtifacts() {
  if (!state.activeProjectUuid) return;
  state.artifacts = await api(`/api/projects/${state.activeProjectUuid}/artifacts`);
}

async function loadRuns() {
  if (!state.activeProjectUuid) return;
  const params = new URLSearchParams({
    pageNum: String(state.runFilters.pageNum),
    pageSize: String(state.runFilters.pageSize),
    projectUuid: state.activeProjectUuid
  });
  if (state.runFilters.agentType) params.set("agentType", state.runFilters.agentType);
  if (state.runFilters.status) params.set("status", state.runFilters.status);

  const page = await api(`/api/agent/runs?${params.toString()}`);
  state.runs = page.records || page;
}

async function runDemoWorkflow() {
  state.streamRunning = true;
  state.events = [];
  state.demoUrl = "";
  state.rawOutput = null;
  activeTab.value = "demo";

  try {
    const response = await fetch(`${state.baseUrl}/api/demo/game/stream`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({
        projectUuid: state.activeProjectUuid,
        title: state.generation.title,
        idea: state.generation.idea,
        context: state.generation.context
      })
    });

    if (!response.ok || !response.body) {
      throw new Error(`SSE 请求失败：HTTP ${response.status}`);
    }

    await readSseStream(response.body);
    await Promise.all([loadArtifacts(), loadRuns()]);
    updateGameConfigFromArtifacts();
  } catch (error) {
    state.events.unshift({
      stage: "FAILED",
      status: "FAILED",
      message: error.message,
      eventTime: new Date().toISOString()
    });
  } finally {
    state.streamRunning = false;
  }
}

async function readSseStream(body) {
  const reader = body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split("\n\n");
    buffer = chunks.pop() || "";
    chunks.forEach(parseSseChunk);
  }
  if (buffer.trim()) {
    parseSseChunk(buffer);
  }
}

function parseSseChunk(chunk) {
  const dataLine = chunk.split("\n").find((line) => line.startsWith("data:"));
  if (!dataLine) return;
  const payload = JSON.parse(dataLine.replace(/^data:\s*/, ""));
  state.events.unshift(payload);
  state.rawOutput = payload.data || payload;
  if (payload.demoUrl) {
    state.demoUrl = payload.demoUrl;
  }
}

async function runSingleGameConfig() {
  const run = await api("/api/agent/run", {
    method: "POST",
    body: JSON.stringify({
      projectUuid: state.activeProjectUuid,
      agentType: "GAME_CONFIG_GENERATE",
      title: state.generation.title,
      content: state.generation.idea,
      context: state.generation.context
    })
  });
  const config = extractGameConfig(run.outputContent);
  if (config) {
    state.gameConfig = config;
    state.configValidation = validateGameConfig(config);
    state.rawOutput = run;
  }
}

function updateGameConfigFromArtifacts() {
  const { config, artifact } = extractGameConfigFromArtifacts(state.artifacts);
  const validation = validateGameConfig(config || defaultGameConfig);
  state.gameConfig = validation.config;
  state.configValidation = validation;
  if (artifact) {
    state.rawOutput = artifact;
  }
}

function openDemo() {
  if (state.demoUrl) {
    window.open(state.demoUrl, "_blank", "noopener,noreferrer");
  }
}

function fillExample() {
  state.generation = {
    title: "像素地牢探索 Demo",
    idea: "做一个像素风地牢探索游戏，玩家需要收集 3 个宝石，躲开敌人，最后抵达出口。",
    context: "目标平台是浏览器，玩法要简单直观，适合 1 分钟内展示。"
  };
}

function latestEvent() {
  return state.events[0];
}
</script>
