import { test, expect } from "@playwright/test";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { defaultGameConfig } from "../src/game/gameConfig.js";

let apiServer;
let vite;
let projects = [];

const project = {
  projectUuid: "project-browser-e2e",
  name: "浏览器验收项目",
  gameType: "top_down_collect",
  targetPlatform: "web",
  description: "由浏览器验收创建的项目。",
  status: "CREATED"
};

const runSnapshot = {
  workflowRunUuid: "run-live",
  status: "SUCCESS",
  attempt: 1,
  lastSequence: 4,
  timeTakenMs: 320,
  allowedActions: [],
  steps: [
    { stepKey: "concept", stepOrder: 1, status: "SUCCESS", attempt: 1 },
    { stepKey: "design", stepOrder: 2, status: "SUCCESS", attempt: 1 },
    { stepKey: "generate", stepOrder: 3, status: "SUCCESS", attempt: 1 },
    { stepKey: "evaluate", stepOrder: 4, status: "SUCCESS", attempt: 1 }
  ],
  artifacts: [{ artifactUuid: "artifact-game-config", type: "GAME_CONFIG", displayName: "可试玩游戏", status: "AVAILABLE" }]
};

function json(response, status, data) {
  response.writeHead(status, { "Content-Type": "application/json" });
  response.end(JSON.stringify(data));
}

async function waitFor(url) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    try { if ((await fetch(url)).ok) return; } catch { /* service is still starting */ }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Timed out waiting for ${url}`);
}

function sendSse(response, snapshot) {
  response.writeHead(200, { "Content-Type": "text/event-stream", "Cache-Control": "no-cache" });
  response.end(`event: snapshot\ndata: ${JSON.stringify(snapshot)}\n\n`);
}

test.beforeAll(async () => {
  projects = [];
  apiServer = createServer((request, response) => {
    response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:5174");
    response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Last-Event-ID, Idempotency-Key");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    if (request.method === "OPTIONS") { response.writeHead(204); response.end(); return; }

    const url = new URL(request.url, "http://localhost:8080");
    if (request.method === "POST" && url.pathname === "/api/auth/register") return json(response, 200, { code: 0, data: null });
    if (request.method === "POST" && url.pathname === "/api/auth/login") return json(response, 200, { code: 0, data: { token: "browser-e2e-session", user: { username: "browser-e2e" } } });
    if (request.method === "GET" && url.pathname === "/api/auth/me") return json(response, 200, { code: 0, data: { username: "browser-e2e" } });
    if (request.method === "GET" && url.pathname === "/api/projects") return json(response, 200, { code: 0, data: projects });
    if (request.method === "POST" && url.pathname === "/api/projects") {
      projects = [project];
      return json(response, 200, { code: 0, data: project });
    }
    if (request.method === "GET" && url.pathname === `/api/projects/${project.projectUuid}`) return json(response, 200, { code: 0, data: project });
    if (request.method === "POST" && url.pathname === `/api/v1/projects/${project.projectUuid}/workflow-runs`) {
      return json(response, 202, { code: 0, data: { workflowRunUuid: runSnapshot.workflowRunUuid } });
    }
    if (request.method === "GET" && url.pathname === `/api/v1/workflow-runs/${runSnapshot.workflowRunUuid}`) return json(response, 200, { code: 0, data: runSnapshot });
    if (request.method === "GET" && url.pathname === `/api/v1/workflow-runs/${runSnapshot.workflowRunUuid}/events`) return sendSse(response, runSnapshot);
    if (request.method === "GET" && url.pathname === "/api/artifacts/artifact-game-config") {
      return json(response, 200, { code: 0, data: { artifactUuid: "artifact-game-config", type: "GAME_CONFIG", status: "AVAILABLE", content: defaultGameConfig } });
    }
    return json(response, 404, { code: 404, message: "Not found" });
  });
  await new Promise((resolve) => apiServer.listen(8080, "127.0.0.1", resolve));
  vite = spawn(process.execPath, ["node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port", "5174"], { stdio: "ignore" });
  await waitFor("http://127.0.0.1:5174");
});

test.afterAll(async () => {
  vite?.kill();
  await new Promise((resolve) => apiServer?.close(resolve));
});

async function register(page) {
  await page.goto("/");
  await page.getByRole("tab", { name: "注册账号" }).click();
  await page.getByPlaceholder("用户名（4-20 个字符）").fill("browser-e2e");
  await page.getByPlaceholder("密码（6-32 个字符）").fill("Browser-e2e-1");
  await page.getByPlaceholder("再次输入密码").fill("Browser-e2e-1");
  await page.getByRole("button", { name: "注册并登录" }).click();
  await expect(page.getByRole("heading", { name: "创建项目" })).toBeVisible();
}

test("registration, project lifecycle, submission, refresh and Phaser preview stay user-visible", async ({ page }) => {
  await register(page);
  await expect(page.getByText("还没有项目。请先创建你的第一个游戏项目。")).toBeVisible();
  await page.getByLabel("项目名称").fill(project.name);
  await page.getByLabel("游戏类型").fill(project.gameType);
  await page.getByLabel("目标平台").fill(project.targetPlatform);
  await page.getByLabel("项目描述").fill(project.description);
  await page.getByRole("button", { name: "创建项目" }).click();
  await expect(page.getByRole("button", { name: "开始生成" })).toBeVisible();
  await expect(page.getByLabel("项目 UUID")).toHaveCount(0);

  await page.getByRole("button", { name: "返回项目" }).click();
  await page.getByRole("button", { name: project.name }).click();
  await page.getByLabel("游戏想法").fill("制作一款收集宝石并抵达出口的小游戏。");
  const response = page.waitForResponse((item) => item.request().method() === "POST" && item.url().endsWith(`/projects/${project.projectUuid}/workflow-runs`));
  await page.getByRole("button", { name: "开始生成" }).click();
  expect((await response).status()).toBe(202);
  await expect(page).toHaveURL(/\/workflow-runs\/run-live$/);
  await expect(page.getByRole("heading", { name: "SUCCESS" })).toBeVisible();
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true");

  await page.reload();
  await expect(page.getByRole("heading", { name: "SUCCESS" })).toBeVisible();
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true");
});

test("the run detail keeps controls inside both desktop and 375px viewports", async ({ page }) => {
  await register(page);
  for (const viewport of [{ width: 1440, height: 900 }, { width: 375, height: 812 }]) {
    await page.setViewportSize(viewport);
    await page.goto("/workflow-runs/run-live");
    await expect(page.getByRole("heading", { name: "SUCCESS" })).toBeVisible();
    await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true");
    const layout = await page.locator("body").evaluate((body) => ({
      overflow: document.documentElement.scrollWidth > window.innerWidth,
      controls: [...body.querySelectorAll("button")].map((button) => button.getBoundingClientRect()).every((box) => box.left >= 0 && box.right <= window.innerWidth && box.bottom >= box.top)
    }));
    expect(layout.overflow).toBeFalsy();
    expect(layout.controls).toBeTruthy();
  }
});
