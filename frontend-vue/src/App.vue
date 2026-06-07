<script setup>
import { computed, onMounted, reactive } from "vue";

const AGENT_TYPES = ["GAME_CONCEPT", "CORE_LOOP_DESIGN", "TASK_BREAKDOWN"];

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
  message: ""
});

const activeProject = computed(() => {
  return state.projects.find((project) => project.projectUuid === state.activeProjectUuid) || state.projects[0] || null;
});

const statusText = computed(() => {
  if (state.streamRunning) return "RUNNING";
  if (state.demoUrl) return "SUCCESS";
  return "READY";
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

            <div class="agent-buttons">
              <button v-for="type in AGENT_TYPES" :key="type" class="ghost" type="button" @click="runSingleAgent(type)">
                {{ type }}
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
                <span :class="['badge', event.status?.toLowerCase()]">{{ event.status }}</span>
                <strong>{{ event.stage }}</strong>
                <p>{{ event.message }}</p>
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
