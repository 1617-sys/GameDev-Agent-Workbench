<script setup>
import { computed, onMounted, reactive } from "vue";

const AGENT_TYPES = ["GAME_CONCEPT", "CORE_LOOP_DESIGN", "TASK_BREAKDOWN"];

const AGENT_TYPE_LABELS = {
  GAME_CONCEPT: "游戏概念",
  CORE_LOOP_DESIGN: "核心循环",
  TASK_BREAKDOWN: "任务拆解"
};

const STAGE_LABELS = {
  WORKFLOW_STARTED: "工作流启动",
  GAME_CONCEPT: "游戏概念生成",
  CORE_LOOP_DESIGN: "核心循环设计",
  TASK_BREAKDOWN: "开发任务拆解",
  GAME_BUILD: "可玩 Demo 构建",
  COMPLETED: "生成完成",
  FAILED: "生成失败",
  RAW: "原始事件"
};

const STATUS_LABELS = {
  READY: "待运行",
  RUNNING: "运行中",
  SUCCESS: "成功",
  FAILED: "失败",
  TIMEOUT: "超时",
  INFO: "提示"
};

const DEFAULT_PROMPT_TEMPLATES = [
  {
    agentType: "GAME_CONCEPT",
    name: "默认游戏概念生成模板",
    systemPrompt:
      "你是一个专业的游戏创意策划 Agent，擅长把用户的粗略想法转化为清晰、可执行、适合 MVP 开发的游戏设计方案。",
    userPromptTemplate:
      "任务标题：{title}\n\n用户游戏想法：{content}\n\n补充上下文：{context}\n\n请输出：1. 一句话概念；2. 核心卖点；3. 目标玩家；4. 美术风格；5. MVP 范围。",
    version: 1,
    status: "ACTIVE"
  },
  {
    agentType: "CORE_LOOP_DESIGN",
    name: "默认核心循环设计模板",
    systemPrompt:
      "你是一个专业的玩法系统设计 Agent，擅长把游戏概念拆解成玩家可以反复体验的核心循环。",
    userPromptTemplate:
      "任务标题：{title}\n\n游戏想法：{content}\n\n上一步输出和上下文：{context}\n\n请输出：1. 玩家目标；2. 核心操作；3. 奖励反馈；4. 失败条件；5. 3 分钟可演示玩法循环。",
    version: 1,
    status: "ACTIVE"
  },
  {
    agentType: "TASK_BREAKDOWN",
    name: "默认开发任务拆解模板",
    systemPrompt:
      "你是一个游戏项目开发拆解 Agent，擅长把玩法方案拆成前端、后端、资源和测试任务。",
    userPromptTemplate:
      "任务标题：{title}\n\n游戏想法：{content}\n\n前置设计结果：{context}\n\n请输出：1. 前端任务；2. 后端任务；3. 资源任务；4. 测试任务；5. 今日最小可交付清单。",
    version: 1,
    status: "ACTIVE"
  }
];

const state = reactive({
  baseUrl: localStorage.getItem("gaw.vue.baseUrl") || "http://localhost:8080",
  token: localStorage.getItem("gaw.vue.token") || "",
  user: readJson(localStorage.getItem("gaw.vue.user")),
  authMode: "login",
  auth: {
    username: "vandick",
    password: "123456"
  },
  projectForm: {
    name: "像素地牢 Demo",
    gameType: "RPG",
    targetPlatform: "PC",
    description: "一个由 AI 工作流生成的最小可玩小游戏原型。"
  },
  demoForm: {
    title: "像素地牢探索 Demo",
    idea: "做一个像素风地牢探索游戏，玩家收集宝石并到达出口。",
    context: "目标平台是浏览器，优先做成能快速演示的 MVP。"
  },
  projects: [],
  activeProjectUuid: localStorage.getItem("gaw.vue.activeProjectUuid") || "",
  runFilters: {
    agentType: "ALL",
    status: "ALL",
    pageNum: 1,
    pageSize: 10
  },
  runsPage: {
    total: 0,
    pages: 0,
    current: 1,
    size: 10
  },
  runs: [],
  artifacts: [],
  events: [],
  output: null,
  demoUrl: "",
  health: "UNKNOWN",
  loading: false,
  streamRunning: false,
  templateLoading: false,
  message: ""
});

const activeProject = computed(() => {
  return state.projects.find((project) => project.projectUuid === state.activeProjectUuid) || state.projects[0] || null;
});

const statusText = computed(() => {
  if (state.streamRunning) return STATUS_LABELS.RUNNING;
  if (state.demoUrl) return STATUS_LABELS.SUCCESS;
  return STATUS_LABELS.READY;
});

onMounted(async () => {
  await checkHealth();
  if (state.token) {
    await refreshAll();
  }
});

function readJson(value) {
  try {
    return value ? JSON.parse(value) : null;
  } catch {
    return null;
  }
}

function saveBaseUrl() {
  state.baseUrl = state.baseUrl.trim().replace(/\/$/, "");
  localStorage.setItem("gaw.vue.baseUrl", state.baseUrl);
  checkHealth();
}

async function api(path, options = {}) {
  const headers = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
    ...(options.headers || {})
  };

  const response = await fetch(`${state.baseUrl}${path}`, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(payload?.message || `HTTP ${response.status}`);
  }
  if (payload && payload.code !== 0) {
    throw new Error(payload.message || "Request failed");
  }
  return payload?.data;
}

async function checkHealth() {
  try {
    await fetch(`${state.baseUrl}/api/health`);
    state.health = "ONLINE";
  } catch {
    state.health = "OFFLINE";
  }
}

async function loginOrRegister() {
  state.loading = true;
  state.message = "";
  try {
    if (state.authMode === "register") {
      await api("/api/auth/register", {
        method: "POST",
        body: {
          username: state.auth.username,
          password: state.auth.password
        }
      });
      state.authMode = "login";
      state.message = "注册成功，请登录。";
      return;
    }

    const data = await api("/api/auth/login", {
      method: "POST",
      body: {
        username: state.auth.username,
        password: state.auth.password
      }
    });
    state.token = data.token;
    state.user = data.user;
    localStorage.setItem("gaw.vue.token", state.token);
    localStorage.setItem("gaw.vue.user", JSON.stringify(state.user));
    await refreshAll();
    state.message = "登录成功。";
  } catch (error) {
    state.message = error.message;
  } finally {
    state.loading = false;
  }
}

function logout() {
  state.token = "";
  state.user = null;
  state.projects = [];
  state.runs = [];
  state.artifacts = [];
  state.events = [];
  state.demoUrl = "";
  localStorage.removeItem("gaw.vue.token");
  localStorage.removeItem("gaw.vue.user");
  localStorage.removeItem("gaw.vue.activeProjectUuid");
}

async function refreshAll() {
  await checkHealth();
  if (!state.token) return;
  await loadProjects();
  await Promise.allSettled([loadRuns(), loadArtifacts()]);
}

async function loadProjects() {
  state.projects = await api("/api/projects");
  if (!state.activeProjectUuid && state.projects.length > 0) {
    selectProject(state.projects[0].projectUuid);
  }
}

function selectProject(projectUuid) {
  state.activeProjectUuid = projectUuid;
  state.runFilters.pageNum = 1;
  localStorage.setItem("gaw.vue.activeProjectUuid", projectUuid);
  loadArtifacts();
  loadRuns();
}

async function createProject() {
  state.loading = true;
  state.message = "";
  try {
    const project = await api("/api/projects", {
      method: "POST",
      body: { ...state.projectForm }
    });
    await loadProjects();
    selectProject(project.projectUuid);
    state.message = "项目创建成功。";
  } catch (error) {
    state.message = error.message;
  } finally {
    state.loading = false;
  }
}

function fillDemoExample() {
  state.projectForm.name = "像素地牢 Demo";
  state.projectForm.gameType = "RPG";
  state.projectForm.targetPlatform = "PC";
  state.projectForm.description = "一个由 AI 工作流生成的最小可玩小游戏原型。";
  state.demoForm.title = "像素地牢探索 Demo";
  state.demoForm.idea = "做一个像素风地牢探索游戏，玩家需要收集宝石、躲避怪物，并找到出口。";
  state.demoForm.context = "目标平台是浏览器，优先做成功能快速演示的 MVP，玩法要简单直观。";
  state.message = "已填入示范用例。下一步可以新建项目，或直接为当前项目创建三步默认模板。";
}

async function createDefaultPromptTemplates() {
  if (!state.token) {
    state.message = "请先登录，再创建 Prompt 模板。";
    return;
  }

  state.templateLoading = true;
  state.message = "";
  try {
    const createdTypes = [];
    for (const template of DEFAULT_PROMPT_TEMPLATES) {
      const created = await api("/api/promptTemplate/modify", {
        method: "POST",
        body: {
          ...template,
          projectUuid: activeProject.value?.projectUuid || ""
        }
      });
      createdTypes.push(AGENT_TYPE_LABELS[created.agentType] || created.agentType);
    }
    state.message = `三步默认模板已创建：${createdTypes.join("、")}。现在可以重新运行 Demo。`;
  } catch (error) {
    state.message = `模板创建失败：${explainError(error.message)}`;
  } finally {
    state.templateLoading = false;
  }
}

async function loadRuns() {
  if (!state.token) return;
  const query = new URLSearchParams({
    pageNum: String(state.runFilters.pageNum),
    pageSize: String(state.runFilters.pageSize)
  });
  if (activeProject.value?.projectUuid) {
    query.set("projectUuid", activeProject.value.projectUuid);
  }
  if (state.runFilters.agentType !== "ALL") {
    query.set("agentType", state.runFilters.agentType);
  }
  if (state.runFilters.status !== "ALL") {
    query.set("status", state.runFilters.status);
  }
  const page = await api(`/api/agent/runs?${query.toString()}`);
  state.runs = page?.records || [];
  state.runsPage = {
    total: page?.total || 0,
    pages: page?.pages || 0,
    current: page?.current || state.runFilters.pageNum,
    size: page?.size || state.runFilters.pageSize
  };
}

function resetRunFilters() {
  state.runFilters.agentType = "ALL";
  state.runFilters.status = "ALL";
  state.runFilters.pageNum = 1;
  state.runFilters.pageSize = 10;
  loadRuns();
}

function changeRunPage(delta) {
  const next = state.runFilters.pageNum + delta;
  if (next < 1) return;
  if (state.runsPage.pages && next > state.runsPage.pages) return;
  state.runFilters.pageNum = next;
  loadRuns();
}

async function loadArtifacts() {
  if (!activeProject.value?.projectUuid) {
    state.artifacts = [];
    return;
  }
  state.artifacts = await api(`/api/projects/${activeProject.value.projectUuid}/artifacts`);
}

async function runSingleAgent(agentType) {
  if (!activeProject.value) {
    state.message = "请先创建或选择项目。";
    return;
  }
  state.loading = true;
  state.output = null;
  state.message = "";
  try {
    const data = await api("/api/agent/run", {
      method: "POST",
      body: {
        projectUuid: activeProject.value.projectUuid,
        agentType,
        title: state.demoForm.title,
        content: state.demoForm.idea,
        context: state.demoForm.context
      }
    });
    state.output = data;
    await loadRuns();
  } catch (error) {
    state.message = error.message;
  } finally {
    state.loading = false;
  }
}

async function runDemoStream() {
  if (!activeProject.value) {
    state.message = "请先创建或选择项目。";
    return;
  }

  state.events = [];
  state.output = null;
  state.demoUrl = "";
  state.message = "";
  state.streamRunning = true;

  try {
    const response = await fetch(`${state.baseUrl}/api/demo/game/stream`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${state.token}`
      },
      body: JSON.stringify({
        projectUuid: activeProject.value.projectUuid,
        title: state.demoForm.title,
        idea: state.demoForm.idea,
        context: state.demoForm.context
      })
    });

    if (!response.ok || !response.body) {
      throw new Error(`SSE failed: HTTP ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("UTF-8");
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split("\n\n");
      buffer = blocks.pop() || "";

      blocks.forEach(handleSseBlock);
    }

    await Promise.allSettled([loadRuns(), loadArtifacts()]);
  } catch (error) {
    state.message = error.message;
  } finally {
    state.streamRunning = false;
  }
}

function handleSseBlock(block) {
  const dataLines = block
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trim());

  if (dataLines.length === 0) return;

  const raw = dataLines.join("\n");
  try {
    const event = JSON.parse(raw);
    state.events.unshift(event);
    state.output = event.data || event;
    const demoUrl = event.demoUrl || event.data?.demoUrl;
    if (demoUrl) {
      state.demoUrl = demoUrl;
    }
  } catch {
    state.events.unshift({ stage: "RAW", status: "INFO", message: raw });
  }
}

function stageLabel(stage) {
  return STAGE_LABELS[stage] || stage || "未知阶段";
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status || "未知状态";
}

function translateMessage(message) {
  if (!message) return "";
  if (message.includes("Active prompt template not found")) {
    return "缺少激活的 Prompt 模板，请先创建三步默认模板。";
  }
  if (message.includes("Failed to call Python service")) {
    return "调用 Python Agent 失败，请确认 Python 服务已启动，并检查模板字段是否完整。";
  }
  if (message.includes("Game demo workflow started")) return "游戏 Demo 工作流已启动";
  if (message.includes("Generating game concept")) return "正在生成游戏概念";
  if (message.includes("Designing core gameplay loop")) return "正在设计核心循环";
  if (message.includes("Breaking down development tasks")) return "正在拆解开发任务";
  if (message.includes("Building playable game demo")) return "正在构建可玩 Demo";
  if (message.includes("Playable game demo generated")) return "可玩 Demo 已生成";
  if (message.includes("Game demo workflow completed")) return "游戏 Demo 工作流已完成";
  return message;
}

function eventHint(event) {
  const message = event?.message || "";
  if (message.includes("Active prompt template not found")) {
    return "操作建议：先点击“创建三步默认模板”，确保 GAME_CONCEPT、CORE_LOOP_DESIGN、TASK_BREAKDOWN 都有 ACTIVE 模板。";
  }
  if (message.includes("Failed to call Python service")) {
    return "操作建议：确认 Python 服务运行在 127.0.0.1:8000，并单独调用 /agent/game-concept 测试。";
  }
  return "";
}

function explainError(message) {
  if (!message) return "请查看后端日志定位具体原因。";
  if (message.includes("Token is invalid") || message.includes("Unauthorized")) {
    return "登录态失效，请重新登录后再试。";
  }
  if (message.includes("Project not found")) {
    return "项目不存在或不属于当前用户，请先创建/选择自己的项目。";
  }
  return message;
}

function formatJson(value) {
  if (!value) return "暂无输出";
  return JSON.stringify(value, null, 2);
}

function openDemo() {
  if (state.demoUrl) {
    window.open(state.demoUrl, "_blank", "noopener,noreferrer");
  }
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="logo">GA</div>
        <div>
          <strong>GameDev Agent</strong>
          <span>AI Workflow Studio</span>
        </div>
      </div>

      <nav>
        <a href="#workspace">工作台</a>
        <a href="#projects">项目</a>
        <a href="#templates">模板</a>
        <a href="#runs">运行记录</a>
        <a href="#artifacts">产物库</a>
      </nav>

      <div class="sidebar-footer">
        <span :class="['dot', state.health.toLowerCase()]"></span>
        <span>{{ state.health }}</span>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <p class="eyebrow">AI Game Workflow</p>
          <h1>游戏设计工作台</h1>
          <p class="subline">
            {{ activeProject ? `${activeProject.name} / ${activeProject.projectUuid}` : "请先创建或选择项目" }}
          </p>
        </div>
        <div class="top-actions">
          <input v-model="state.baseUrl" class="base-url" />
          <button class="ghost" type="button" @click="saveBaseUrl">保存地址</button>
          <button class="ghost" type="button" @click="refreshAll">刷新</button>
          <button v-if="state.token" type="button" @click="logout">退出</button>
        </div>
      </header>

      <p v-if="state.message" class="message">{{ state.message }}</p>

      <section v-if="!state.token" class="auth-panel">
        <div class="section-title">
          <h2>{{ state.authMode === "login" ? "登录" : "注册" }}</h2>
          <div class="segmented">
            <button :class="{ active: state.authMode === 'login' }" type="button" @click="state.authMode = 'login'">登录</button>
            <button :class="{ active: state.authMode === 'register' }" type="button" @click="state.authMode = 'register'">注册</button>
          </div>
        </div>
        <div class="form-grid two">
          <label>
            账号
            <input v-model="state.auth.username" />
          </label>
          <label>
            密码
            <input v-model="state.auth.password" type="password" />
          </label>
        </div>
        <button type="button" :disabled="state.loading" @click="loginOrRegister">
          {{ state.loading ? "处理中" : "提交" }}
        </button>
      </section>

      <template v-else>
        <section id="workspace" class="workspace-grid">
          <div class="panel project-panel">
            <div class="section-title">
              <h2>项目</h2>
              <button class="mini" type="button" @click="createProject">新建</button>
            </div>

            <div class="form-grid">
              <label>
                名称
                <input v-model="state.projectForm.name" />
              </label>
              <label>
                类型
                <input v-model="state.projectForm.gameType" />
              </label>
              <label>
                平台
                <input v-model="state.projectForm.targetPlatform" />
              </label>
              <label>
                描述
                <textarea v-model="state.projectForm.description" rows="3"></textarea>
              </label>
            </div>

            <div id="projects" class="project-list">
              <button
                v-for="project in state.projects"
                :key="project.projectUuid"
                :class="['project-item', { active: project.projectUuid === activeProject?.projectUuid }]"
                type="button"
                @click="selectProject(project.projectUuid)"
              >
                <strong>{{ project.name }}</strong>
                <span>{{ project.gameType }} / {{ project.targetPlatform }} / {{ project.status }}</span>
              </button>
            </div>
          </div>

          <div class="panel workflow-panel">
            <div class="section-title">
              <div>
                <h2>Demo Workflow</h2>
                <p>{{ statusText }}</p>
              </div>
              <button type="button" :disabled="state.streamRunning" @click="runDemoStream">
                {{ state.streamRunning ? "生成中" : "一键生成可玩 Demo" }}
              </button>
            </div>

            <div class="form-grid">
              <label>
                标题
                <input v-model="state.demoForm.title" />
              </label>
              <label>
                游戏想法
                <textarea v-model="state.demoForm.idea" rows="6"></textarea>
              </label>
              <label>
                上下文
                <textarea v-model="state.demoForm.context" rows="4"></textarea>
              </label>
            </div>

            <div id="templates" class="demo-guide">
              <div>
                <strong>演示前准备</strong>
                <p>如果运行失败提示缺少模板，先创建三步默认模板；如果不知道填什么，先填入示范用例。</p>
              </div>
              <div class="guide-actions">
                <button class="ghost" type="button" @click="fillDemoExample">填入示范用例</button>
                <button type="button" :disabled="state.templateLoading" @click="createDefaultPromptTemplates">
                  {{ state.templateLoading ? "创建中" : "创建三步默认模板" }}
                </button>
              </div>
              <ol>
                <li>先登录，并确认 Java 服务地址是当前后端地址。</li>
                <li>创建或选择一个项目。</li>
                <li>点击创建三步默认模板，再运行 Demo Workflow。</li>
              </ol>
            </div>

            <div class="agent-buttons">
              <button v-for="type in AGENT_TYPES" :key="type" class="ghost" type="button" @click="runSingleAgent(type)">
                {{ AGENT_TYPE_LABELS[type] || type }}
              </button>
            </div>

            <button v-if="state.demoUrl" class="demo-button" type="button" @click="openDemo">
              打开可玩 Demo
            </button>
          </div>

          <div class="panel output-panel">
            <div class="section-title">
              <h2>SSE 时间线</h2>
              <span>{{ state.events.length }} events</span>
            </div>
            <div class="timeline">
              <div v-for="event in state.events" :key="`${event.stage}-${event.eventTime}-${event.message}`" class="timeline-item">
                <span :class="['badge', event.status?.toLowerCase()]">{{ statusLabel(event.status) }}</span>
                <strong>{{ stageLabel(event.stage) }}</strong>
                <p>{{ translateMessage(event.message) }}</p>
                <small v-if="eventHint(event)">{{ eventHint(event) }}</small>
              </div>
            </div>
          </div>

          <div class="panel result-panel">
            <div class="section-title">
              <h2>输出</h2>
              <span>{{ state.demoUrl ? "DEMO READY" : "JSON" }}</span>
            </div>
            <pre>{{ formatJson(state.output) }}</pre>
          </div>
        </section>

        <section id="runs" class="panel wide-panel">
          <div class="section-title">
            <div>
              <h2>运行记录</h2>
              <p>支持项目、Agent 类型、状态和分页筛选</p>
            </div>
            <span>{{ state.runsPage.total }} records</span>
          </div>
          <div class="run-toolbar">
            <label>
              Agent 类型
              <select v-model="state.runFilters.agentType" @change="state.runFilters.pageNum = 1; loadRuns()">
                <option value="ALL">全部类型</option>
                <option v-for="type in AGENT_TYPES" :key="type" :value="type">{{ type }}</option>
              </select>
            </label>
            <label>
              状态
              <select v-model="state.runFilters.status" @change="state.runFilters.pageNum = 1; loadRuns()">
                <option value="ALL">全部状态</option>
                <option value="RUNNING">RUNNING</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="FAILED">FAILED</option>
                <option value="TIMEOUT">TIMEOUT</option>
              </select>
            </label>
            <label>
              每页
              <select v-model.number="state.runFilters.pageSize" @change="state.runFilters.pageNum = 1; loadRuns()">
                <option :value="5">5 条</option>
                <option :value="10">10 条</option>
                <option :value="20">20 条</option>
              </select>
            </label>
            <button class="ghost" type="button" @click="resetRunFilters">重置</button>
          </div>
          <div class="list-table">
            <div v-for="run in state.runs" :key="run.runUuid" class="row">
              <strong>{{ run.agentType }}</strong>
              <span>{{ run.status }}</span>
              <span>{{ run.timeTakenMs }} ms</span>
              <span>{{ run.runUuid }}</span>
            </div>
            <p v-if="state.runs.length === 0" class="empty">暂无运行记录</p>
          </div>
          <div class="pager">
            <button class="ghost" type="button" :disabled="state.runFilters.pageNum <= 1" @click="changeRunPage(-1)">
              上一页
            </button>
            <span>第 {{ state.runsPage.current }} / {{ state.runsPage.pages || 1 }} 页</span>
            <button
              class="ghost"
              type="button"
              :disabled="state.runsPage.pages && state.runFilters.pageNum >= state.runsPage.pages"
              @click="changeRunPage(1)"
            >
              下一页
            </button>
          </div>
        </section>

        <section id="artifacts" class="panel wide-panel">
          <div class="section-title">
            <h2>产物库</h2>
            <span>{{ state.artifacts.length }} artifacts</span>
          </div>
          <div class="artifact-grid">
            <article v-for="artifact in state.artifacts" :key="artifact.artifactUuid" class="artifact">
              <span>{{ artifact.artifactType }}</span>
              <h3>{{ artifact.title }}</h3>
              <p>{{ artifact.content }}</p>
            </article>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>
