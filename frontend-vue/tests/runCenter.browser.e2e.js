import { test, expect } from "@playwright/test";
import { createServer } from "node:http";
import { spawn } from "node:child_process";

const json = (response, status, data) => { response.writeHead(status, { "Content-Type": "application/json" }); response.end(JSON.stringify(data)); };
const snapshot = (uuid, status, lastSequence, allowedActions = [], artifacts = []) => ({ workflowRunUuid: uuid, status, attempt: status === "RUNNING" ? 1 : 1, lastSequence, allowedActions, steps: [{ stepKey: "design", stepOrder: 1, status: status === "FAILED" ? "FAILED" : "SUCCESS", attempt: 1 }], artifacts });
let apiServer;
let vite;

async function waitFor(url) {
  const until = Date.now() + 10_000;
  while (Date.now() < until) {
    try { if ((await fetch(url)).ok) return; } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Timed out waiting for ${url}`);
}

test.beforeAll(async () => {
  apiServer = createServer((request, response) => {
    response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:5174");
    response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Last-Event-ID, Idempotency-Key");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    if (request.method === "OPTIONS") { response.writeHead(204); response.end(); return; }
    const url = new URL(request.url, "http://127.0.0.1:8080");
    if (request.method === "POST" && url.pathname === "/api/auth/login") return json(response, 200, { code: 0, data: { token: "test-session" } });
    if (request.method === "POST" && /\/projects\/[^/]+\/workflow-runs$/.test(url.pathname)) return json(response, 202, { code: 0, data: { workflowRunUuid: "run-live" } });
    const knowledge = url.pathname.match(/^\/api\/projects\/([^/]+)\/knowledge-documents$/);
    if (knowledge) {
      if (knowledge[1] === "foreign") return json(response, 404, { code: 404, message: "Not found" });
      if (request.method === "POST") return json(response, 200, { code: 0, data: { documentUuid: "document-new", status: "UPLOADED", contentHash: "hash" } });
      return json(response, 200, { code: 0, data: { capabilities: { upload: true, invalidate: false, delete: false }, documents: [{ documentUuid: "document-1", name: "very-long-authorized-project-guide-name-that-must-wrap-safely.md", sourceType: "UPLOAD", contentHashSummary: "0123456789ab", version: 3, status: "READY", indexedAt: "2026-07-13T12:00:00" }] } });
    }
    const match = url.pathname.match(/^\/api\/v1\/workflow-runs\/([^/]+)(?:\/(events|cancel|retry|rag-evidence))?$/);
    if (!match) return json(response, 404, { code: 404, message: "Not found" });
    const [, uuid, action] = match;
    if (uuid === "forbidden") return json(response, 404, { code: 404, message: "Not found" });
    if (action === "cancel") return json(response, 202, { code: 0, data: snapshot(uuid, "CANCELED", 2) });
    if (action === "retry") return json(response, 202, { code: 0, data: snapshot(uuid, "RUNNING", 3, ["cancel"]) });
    if (action === "rag-evidence") return json(response, 200, { code: 0, data: uuid === "artifact-run" ? [{ stepKey: "design", agentRunUuid: "agent-1", ragEnabled: true, ragStatus: "AVAILABLE", mock: false, contextBudget: 8000, retrievalVersion: "retrieval-v1", chunkingVersion: "chunking-v1", embeddingModel: "fake-embedding-v1", references: [{ documentUuid: "document-1", documentVersion: 3, chunkUuid: "chunk-1", rank: 1, score: 0.91 }], comparison: { status: "COMPARABLE", promptVersionId: 7, provider: "fixture", modelName: "fixture-model", from: "2026-06-13T12:00:00", to: "2026-07-13T12:00:01", retrievalVersion: "retrieval-v1", chunkingVersion: "chunking-v1", embeddingModel: "fake-embedding-v1", evaluationVersions: ["RULE:v1"], ragOff: { samples: 2, evaluated: 2, schemaPassRate: 0.5, rulePassRate: 0.5, runtimePassRate: 0.5, p50LatencyMs: 100, p95LatencyMs: 120, estimatedCost: 0, missingCostSamples: 0 }, ragOn: { samples: 2, evaluated: 2, schemaPassRate: 1, rulePassRate: 1, runtimePassRate: 1, p50LatencyMs: 110, p95LatencyMs: 130, estimatedCost: 0, missingCostSamples: 0, emptyRetrieval: 0, failedRetrieval: 0 } } }] : [] });
    if (action === "events") {
      response.writeHead(200, { "Content-Type": "text/event-stream", "Cache-Control": "no-cache" });
      const initial = uuid === "cancel-run" ? snapshot(uuid, "RUNNING", 1, ["cancel"]) : uuid === "retry-run" ? snapshot(uuid, "FAILED", 2, ["retry"]) : snapshot(uuid, "RUNNING", 1, ["cancel"]);
      response.write(`event: snapshot\ndata: ${JSON.stringify(initial)}\n\n`);
      if (uuid === "run-live") response.write(`event: run.terminal\ndata: ${JSON.stringify({ workflowRunUuid: uuid, eventType: "run.terminal", sequence: 2, status: "SUCCESS" })}\n\n`);
      return;
    }
    if (uuid === "artifact-run") return json(response, 200, { code: 0, data: snapshot(uuid, "SUCCESS", 3, [], [{ artifactUuid: "artifact-1", type: "GAME_CONFIG", displayName: "Playable Demo", status: "AVAILABLE", url: "/demo/play" }]) });
    if (uuid === "empty-run") return json(response, 200, { code: 0, data: snapshot(uuid, "SUCCESS", 1) });
    if (uuid === "retry-run") return json(response, 200, { code: 0, data: snapshot(uuid, "FAILED", 2, ["retry"]) });
    if (uuid === "cancel-run") return json(response, 200, { code: 0, data: snapshot(uuid, "RUNNING", 1, ["cancel"]) });
    return json(response, 200, { code: 0, data: snapshot(uuid, "RUNNING", 1, ["cancel"]) });
  });
  await new Promise((resolve) => apiServer.listen(8080, resolve));
  vite = spawn(process.execPath, ["node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port", "5174"], { stdio: "ignore" });
  await waitFor("http://127.0.0.1:5174");
});

test.afterAll(async () => { vite?.kill(); await new Promise((resolve) => apiServer?.close(resolve)); });

async function login(page, path = "/") {
  await page.goto(path);
  await page.getByPlaceholder("用户名").fill("r4-e2e");
  await page.getByPlaceholder("密码").fill("not-persisted");
  await page.getByRole("button", { name: "登录" }).click();
}

test("submits a test workflow and reaches the terminal run snapshot", async ({ page }) => {
  await login(page);
  await expect(page.getByText("新建异步工作流")).toBeVisible();
  await page.getByLabel("项目 UUID").fill("project");
  await page.getByLabel("游戏想法").fill("test game idea");
  await page.getByRole("button", { name: "创建工作流" }).click();
  await expect(page).toHaveURL(/\/workflow-runs\/run-live$/);
  await expect(page.getByRole("heading", { name: "SUCCESS" })).toBeVisible();
});

test("covers cancel, retry, artifact, empty and unauthorized states", async ({ page }) => {
  await login(page);
  await login(page, "/workflow-runs/cancel-run");
  await expect(page.getByRole("button", { name: "取消" })).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click();
  await expect(page.getByRole("heading", { name: "CANCELED" })).toBeVisible();
  await login(page, "/workflow-runs/retry-run");
  await expect(page.getByRole("button", { name: "重试" })).toBeVisible();
  await page.getByRole("button", { name: "重试" }).click();
  await expect(page.getByRole("heading", { name: "RUNNING" })).toBeVisible();
  await login(page, "/workflow-runs/artifact-run");
  await expect(page.getByRole("link", { name: "打开 Demo / 产物" })).toHaveAttribute("href", "/demo/play");
  await login(page, "/workflow-runs/empty-run");
  await expect(page.getByText("暂无可用产物。")).toBeVisible();
  await login(page, "/workflow-runs/forbidden");
  await expect(page.getByRole("alert")).toContainText("Not found");
});

test("desktop and 375px mobile layouts have no horizontal overflow or overlapping controls", async ({ page }) => {
  await login(page);
  for (const viewport of [{ width: 1440, height: 900 }, { width: 375, height: 812 }]) {
    await page.setViewportSize(viewport);
    await login(page, "/workflow-runs/artifact-run");
    await expect(page.getByRole("heading", { name: "SUCCESS" })).toBeVisible();
    await expect(page.getByText("已使用检索来源")).toBeVisible();
    const layout = await page.locator("body").evaluate((body) => ({ overflow: document.documentElement.scrollWidth > window.innerWidth, buttons: [...document.querySelectorAll("button")].map((button) => { const box = button.getBoundingClientRect(); return { left: box.left, right: box.right, top: box.top, bottom: box.bottom }; }) }));
    expect(layout.overflow).toBeFalsy();
    expect(layout.buttons.every((button) => button.left >= 0 && button.right <= viewport.width && button.bottom >= button.top)).toBeTruthy();
    await page.screenshot({ path: test.info().outputPath(`run-center-${viewport.width}.png`), fullPage: true });
  }
});

test("knowledge library uploads safely, rejects foreign projects and fits desktop/mobile", async ({ page }) => {
  await login(page);
  await page.getByLabel("项目 UUID").fill("project");
  await page.getByRole("button", { name: "管理项目知识库" }).click();
  await expect(page).toHaveURL(/\/projects\/project\/knowledge$/);
  await expect(page.getByText("very-long-authorized-project-guide-name-that-must-wrap-safely.md")).toBeVisible();
  await page.getByLabel("上传 Markdown、TXT 或 PDF").setInputFiles({ name: "guide.md", mimeType: "text/markdown", buffer: Buffer.from("safe") });
  await page.getByRole("button", { name: "上传并异步处理" }).click();
  await expect(page.getByRole("status")).toContainText("UPLOADED");
  for (const viewport of [{ width: 1440, height: 900 }, { width: 375, height: 812 }]) {
    await page.setViewportSize(viewport);
    const overflow = await page.locator("body").evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow).toBeFalsy();
    await page.screenshot({ path: test.info().outputPath(`knowledge-${viewport.width}.png`), fullPage: true });
  }
  await login(page, "/projects/foreign/knowledge");
  await expect(page.getByRole("alert")).toContainText("Not found");
  await expect(page.getByText("very-long-authorized-project-guide-name-that-must-wrap-safely.md")).toHaveCount(0);
});
