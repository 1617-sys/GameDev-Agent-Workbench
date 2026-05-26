const state = {
  baseUrl: localStorage.getItem("gaw.baseUrl") || "http://localhost:8080",
  token: localStorage.getItem("gaw.token") || "",
  user: readJson(localStorage.getItem("gaw.user")),
  authMode: "login",
  projects: [],
  activeProject: readJson(localStorage.getItem("gaw.activeProject")),
  runs: [],
  artifacts: [],
  runsPage: {
    current: 1,
    size: 10,
    total: 0,
    pages: 1
  }
};

const els = {
  authPanel: document.querySelector("#authPanel"),
  authMessage: document.querySelector("#authMessage"),
  authTitle: document.querySelector("#authTitle"),
  authSubmitButton: document.querySelector("#authSubmitButton"),
  showLoginButton: document.querySelector("#showLoginButton"),
  showRegisterButton: document.querySelector("#showRegisterButton"),
  baseUrlInput: document.querySelector("#baseUrlInput"),
  connectionDot: document.querySelector("#connectionDot"),
  connectionText: document.querySelector("#connectionText"),
  activeProjectText: document.querySelector("#activeProjectText"),
  projectList: document.querySelector("#projectList"),
  artifactGrid: document.querySelector("#artifactGrid"),
  runsTable: document.querySelector("#runsTable"),
  runsPageText: document.querySelector("#runsPageText"),
  outputBox: document.querySelector("#outputBox"),
  workflowStatus: document.querySelector("#workflowStatus"),
  lastRunMeta: document.querySelector("#lastRunMeta"),
  projectDialog: document.querySelector("#projectDialog")
};

init();

function init() {
  els.baseUrlInput.value = state.baseUrl;
  bindEvents();
  renderAuthMode();
  renderAuthState();
  renderActiveProject();
  checkHealth();
  if (state.token) {
    refreshAll();
  }
}

function bindEvents() {
  document.querySelectorAll(".nav-item").forEach((button) => {
    button.addEventListener("click", () => switchView(button.dataset.view));
  });

  els.showLoginButton.addEventListener("click", () => setAuthMode("login"));
  els.showRegisterButton.addEventListener("click", () => setAuthMode("register"));
  els.authSubmitButton.addEventListener("click", submitAuth);
  document.querySelector("#logoutButton").addEventListener("click", logout);
  document.querySelector("#refreshButton").addEventListener("click", refreshAll);
  document.querySelector("#saveSettingsButton").addEventListener("click", saveSettings);
  document.querySelector("#newProjectButton").addEventListener("click", () => els.projectDialog.showModal());
  document.querySelector("#createProjectButton").addEventListener("click", createProject);
  document.querySelector("#runWorkflowButton").addEventListener("click", runWorkflow);
  document.querySelector("#runAgentButton").addEventListener("click", runAgent);

  ["#runProjectFilter", "#runTypeFilter", "#runStatusFilter", "#runPageSize"].forEach((selector) => {
    document.querySelector(selector).addEventListener("change", () => {
      state.runsPage.current = 1;
      loadRuns();
    });
  });
  document.querySelector("#prevRunsPageButton").addEventListener("click", () => changeRunsPage(-1));
  document.querySelector("#nextRunsPageButton").addEventListener("click", () => changeRunsPage(1));
}

function switchView(viewName) {
  document.querySelectorAll(".nav-item").forEach((button) => {
    button.classList.toggle("active", button.dataset.view === viewName);
  });
  document.querySelectorAll(".view").forEach((view) => view.classList.remove("active"));
  document.querySelector(`#${viewName}View`).classList.add("active");

  if (viewName === "runs") loadRuns();
  if (viewName === "artifacts") loadArtifacts();
}

function setAuthMode(mode) {
  state.authMode = mode;
  els.authMessage.textContent = "";
  renderAuthMode();
}

function renderAuthMode() {
  const isLogin = state.authMode === "login";
  els.authTitle.textContent = isLogin ? "登录工作台" : "注册账号";
  els.authSubmitButton.textContent = isLogin ? "登录" : "注册";
  els.showLoginButton.classList.toggle("active", isLogin);
  els.showRegisterButton.classList.toggle("active", !isLogin);
}

async function submitAuth() {
  if (state.authMode === "register") {
    await register();
    return;
  }
  await login();
}

async function register() {
  const username = document.querySelector("#authUsername").value.trim();
  const password = document.querySelector("#authPassword").value;
  els.authMessage.textContent = "";

  try {
    await request("/api/auth/register", {
      method: "POST",
      body: { username, password },
      auth: false
    });
    els.authMessage.textContent = "注册成功，请登录";
    setAuthMode("login");
  } catch (error) {
    els.authMessage.textContent = error.message;
  }
}

async function login() {
  const username = document.querySelector("#authUsername").value.trim();
  const password = document.querySelector("#authPassword").value;
  els.authMessage.textContent = "";

  try {
    const data = await request("/api/auth/login", {
      method: "POST",
      body: { username, password },
      auth: false
    });
    state.token = data.token;
    state.user = data.user;
    localStorage.setItem("gaw.token", state.token);
    localStorage.setItem("gaw.user", JSON.stringify(state.user));
    renderAuthState();
    await refreshAll();
  } catch (error) {
    els.authMessage.textContent = error.message;
  }
}

function logout() {
  state.token = "";
  state.user = null;
  state.activeProject = null;
  localStorage.removeItem("gaw.token");
  localStorage.removeItem("gaw.user");
  localStorage.removeItem("gaw.activeProject");
  renderAuthState();
  renderProjects();
  renderArtifacts();
  renderRuns();
  renderActiveProject();
}

function saveSettings() {
  state.baseUrl = els.baseUrlInput.value.trim().replace(/\/$/, "");
  localStorage.setItem("gaw.baseUrl", state.baseUrl);
  document.querySelector("#settingsMessage").textContent = "已保存";
  checkHealth();
}

async function refreshAll() {
  await checkHealth();
  if (!state.token) return;
  await loadProjects();
  await Promise.allSettled([loadRuns(), loadArtifacts()]);
}

async function checkHealth() {
  try {
    await fetch(`${state.baseUrl}/api/health`);
    els.connectionDot.classList.add("online");
    els.connectionText.textContent = "Java 后端在线";
  } catch {
    els.connectionDot.classList.remove("online");
    els.connectionText.textContent = "后端未连接";
  }
}

async function loadProjects() {
  const projects = await request("/api/projects");
  state.projects = Array.isArray(projects) ? projects : [];

  if (!state.activeProject && state.projects.length > 0) {
    state.activeProject = state.projects[0];
  }
  if (state.activeProject) {
    const latest = state.projects.find((project) => project.projectUuid === state.activeProject.projectUuid);
    state.activeProject = latest || state.activeProject;
    localStorage.setItem("gaw.activeProject", JSON.stringify(state.activeProject));
  }

  renderProjects();
  renderProjectFilter();
  renderActiveProject();
}

async function createProject(event) {
  event.preventDefault();
  const payload = {
    name: document.querySelector("#projectName").value.trim(),
    gameType: document.querySelector("#projectType").value.trim(),
    targetPlatform: document.querySelector("#projectPlatform").value.trim(),
    description: document.querySelector("#projectDescription").value.trim()
  };

  const project = await request("/api/projects", { method: "POST", body: payload });
  state.activeProject = project;
  localStorage.setItem("gaw.activeProject", JSON.stringify(project));
  els.projectDialog.close();
  clearProjectForm();
  await refreshAll();
}

async function runWorkflow() {
  const project = requireProject();
  if (!project) return;

  const payload = {
    projectUuid: project.projectUuid,
    title: document.querySelector("#workflowTitle").value.trim() || project.name,
    idea: document.querySelector("#ideaInput").value.trim(),
    context: document.querySelector("#contextInput").value.trim()
  };

  await executeWithOutput("WORKFLOW", () => request("/api/workflow/game-design/run", {
    method: "POST",
    body: payload
  }));
}

async function runAgent() {
  const project = requireProject();
  if (!project) return;

  const payload = {
    projectUuid: project.projectUuid,
    agentType: document.querySelector("#agentType").value,
    title: document.querySelector("#workflowTitle").value.trim() || project.name,
    content: document.querySelector("#ideaInput").value.trim(),
    context: document.querySelector("#contextInput").value.trim()
  };

  await executeWithOutput(payload.agentType, () => request("/api/agent/run", {
    method: "POST",
    body: payload
  }));
}

async function executeWithOutput(label, runner) {
  els.workflowStatus.textContent = "RUNNING";
  els.workflowStatus.classList.remove("failed");
  els.outputBox.textContent = "运行中...";
  try {
    const data = await runner();
    els.workflowStatus.textContent = "SUCCESS";
    els.lastRunMeta.textContent = label;
    els.outputBox.textContent = formatOutput(data);
    state.runsPage.current = 1;
    await Promise.allSettled([loadRuns(), loadArtifacts()]);
  } catch (error) {
    els.workflowStatus.textContent = "FAILED";
    els.workflowStatus.classList.add("failed");
    els.outputBox.textContent = error.message;
  }
}

async function loadRuns() {
  if (!state.token) return;
  const params = new URLSearchParams();
  const projectUuid = document.querySelector("#runProjectFilter").value;
  const type = document.querySelector("#runTypeFilter").value;
  const status = document.querySelector("#runStatusFilter").value;
  const pageSize = Number(document.querySelector("#runPageSize").value || 10);

  state.runsPage.size = pageSize;
  params.set("pageNum", state.runsPage.current);
  params.set("pageSize", state.runsPage.size);
  if (projectUuid) params.set("projectUuid", projectUuid);
  if (type) params.set("agentType", type);
  if (status) params.set("status", status);

  const page = await request(`/api/agent/runs?${params.toString()}`);
  state.runs = normalizeList(page);
  state.runsPage.current = Number(page?.current || state.runsPage.current || 1);
  state.runsPage.size = Number(page?.size || state.runsPage.size || pageSize);
  state.runsPage.total = Number(page?.total || 0);
  state.runsPage.pages = Number(page?.pages || Math.max(1, Math.ceil(state.runsPage.total / state.runsPage.size)));
  renderRuns();
}

async function loadArtifacts() {
  if (!state.token || !state.activeProject) {
    renderArtifacts();
    return;
  }
  const artifacts = await request(`/api/projects/${state.activeProject.projectUuid}/artifacts`);
  state.artifacts = Array.isArray(artifacts) ? artifacts : [];
  renderArtifacts();
}

function changeRunsPage(delta) {
  const nextPage = state.runsPage.current + delta;
  if (nextPage < 1 || nextPage > state.runsPage.pages) return;
  state.runsPage.current = nextPage;
  loadRuns();
}

function renderAuthState() {
  const isAuthed = Boolean(state.token);
  els.authPanel.classList.toggle("active", !isAuthed);
  document.querySelectorAll(".view").forEach((view) => {
    view.style.display = isAuthed ? "" : "none";
  });
}

function renderProjects() {
  if (!state.token) {
    els.projectList.innerHTML = "";
    return;
  }
  if (state.projects.length === 0) {
    els.projectList.innerHTML = `<div class="project-card"><div class="project-name">暂无项目</div><div class="meta">点击右上角新建</div></div>`;
    return;
  }

  els.projectList.innerHTML = state.projects.map((project) => `
    <article class="project-card ${state.activeProject?.projectUuid === project.projectUuid ? "active" : ""}" data-project-uuid="${escapeHtml(project.projectUuid)}">
      <div class="project-name">${escapeHtml(project.name)}</div>
      <div class="meta">
        <span>${escapeHtml(project.gameType)}</span>
        <span>${escapeHtml(project.targetPlatform)}</span>
        <span>${escapeHtml(project.status || "ACTIVE")}</span>
      </div>
    </article>
  `).join("");

  els.projectList.querySelectorAll(".project-card").forEach((card) => {
    card.addEventListener("click", async () => {
      state.activeProject = state.projects.find((project) => project.projectUuid === card.dataset.projectUuid);
      localStorage.setItem("gaw.activeProject", JSON.stringify(state.activeProject));
      renderProjects();
      renderProjectFilter();
      renderActiveProject();
      state.runsPage.current = 1;
      await Promise.allSettled([loadRuns(), loadArtifacts()]);
    });
  });
}

function renderProjectFilter() {
  const select = document.querySelector("#runProjectFilter");
  const currentValue = select.value;
  select.innerHTML = `<option value="">全部项目</option>` + state.projects.map((project) => `
    <option value="${escapeHtml(project.projectUuid)}">${escapeHtml(project.name)}</option>
  `).join("");

  if (currentValue && state.projects.some((project) => project.projectUuid === currentValue)) {
    select.value = currentValue;
  } else if (state.activeProject) {
    select.value = state.activeProject.projectUuid;
  }
}

function renderActiveProject() {
  if (!state.activeProject) {
    els.activeProjectText.textContent = "选择项目后开始运行 Agent 工作流";
    return;
  }
  els.activeProjectText.textContent = `${state.activeProject.name} / ${state.activeProject.projectUuid}`;
}

function renderRuns() {
  if (!state.token) {
    els.runsTable.innerHTML = "";
    return;
  }
  if (state.runs.length === 0) {
    els.runsTable.innerHTML = `<div class="table-row"><div class="row-title">暂无运行记录</div></div>`;
  } else {
    els.runsTable.innerHTML = state.runs.map((run) => `
      <article class="table-row">
        <div class="row-title">${escapeHtml(run.agentType || "UNKNOWN")}</div>
        <div class="row-meta">
          <span>${escapeHtml(run.status || "")}</span>
          <span>${escapeHtml(run.projectUuid || "")}</span>
          <span>${escapeHtml(run.runUuid || "")}</span>
          <span>${run.timeTakenMs ?? 0}ms</span>
          <span>${formatTime(run.createdAt)}</span>
        </div>
      </article>
    `).join("");
  }

  els.runsPageText.textContent = `第 ${state.runsPage.current} 页 / 共 ${state.runsPage.pages} 页，共 ${state.runsPage.total} 条`;
  document.querySelector("#prevRunsPageButton").disabled = state.runsPage.current <= 1;
  document.querySelector("#nextRunsPageButton").disabled = state.runsPage.current >= state.runsPage.pages;
}

function renderArtifacts() {
  if (!state.token) {
    els.artifactGrid.innerHTML = "";
    return;
  }
  if (!state.activeProject) {
    els.artifactGrid.innerHTML = `<div class="artifact-card"><div class="artifact-title">请先选择项目</div></div>`;
    return;
  }
  if (state.artifacts.length === 0) {
    els.artifactGrid.innerHTML = `<div class="artifact-card"><div class="artifact-title">暂无产物</div></div>`;
    return;
  }

  els.artifactGrid.innerHTML = state.artifacts.map((artifact) => `
    <article class="artifact-card">
      <div class="artifact-title">${escapeHtml(artifact.title || artifact.artifactType || "Artifact")}</div>
      <div class="meta">
        <span>${escapeHtml(artifact.artifactType || "")}</span>
        <span>${formatTime(artifact.createdAt)}</span>
      </div>
      <div class="artifact-content">${escapeHtml(prettyJsonText(artifact.content))}</div>
    </article>
  `).join("");
}

async function request(path, options = {}) {
  const headers = { "Content-Type": "application/json" };
  if (options.auth !== false && state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const response = await fetch(`${state.baseUrl}${path}`, {
    method: options.method || "GET",
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  const hasApiCode = payload && Object.prototype.hasOwnProperty.call(payload, "code");

  if (response.ok && !hasApiCode) {
    return payload;
  }

  if (!response.ok || (payload && payload.code !== 0)) {
    throw new Error(payload?.message || `请求失败：${response.status}`);
  }

  return payload?.data ?? payload;
}

function normalizeList(value) {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.records)) return value.records;
  return [];
}

function requireProject() {
  if (state.activeProject) return state.activeProject;
  els.outputBox.textContent = "请先创建或选择一个项目";
  return null;
}

function formatOutput(data) {
  if (typeof data === "string") return prettyJsonText(data);
  return JSON.stringify(data, null, 2);
}

function prettyJsonText(value) {
  if (!value) return "";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return String(value);
  }
}

function formatTime(value) {
  if (!value) return "";
  return String(value).replace("T", " ").slice(0, 19);
}

function clearProjectForm() {
  ["#projectName", "#projectType", "#projectPlatform", "#projectDescription"].forEach((selector) => {
    document.querySelector(selector).value = "";
  });
}

function readJson(value) {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
