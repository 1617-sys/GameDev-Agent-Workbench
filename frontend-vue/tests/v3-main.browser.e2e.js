import { test, expect } from "@playwright/test";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { inflateRawSync } from "node:zlib";

const apiBase = process.env.E2E_API_BASE_URL || "http://127.0.0.1:8080";

async function call(request, method, path, { token, body, key } = {}) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (key) headers["Idempotency-Key"] = key;
  const response = await request.fetch(`${apiBase}${path}`, { method, headers, data: body });
  const contentType = response.headers()["content-type"] || "";
  const payload = contentType.includes("json") ? await response.json() : null;
  expect(response.ok(), `${method} ${path}: ${JSON.stringify(payload)}`).toBeTruthy();
  expect(payload?.code, `${method} ${path}: ${JSON.stringify(payload)}`).toBe(0);
  return payload.data;
}

function event(sequence, type, clientElapsedMs, payload = {}) {
  return { eventUuid: crypto.randomUUID(), sequence, type, clientElapsedMs, payload };
}

function unzip(archive) {
  const eocd = archive.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  expect(eocd).toBeGreaterThanOrEqual(0);
  const entryCount = archive.readUInt16LE(eocd + 10);
  let offset = archive.readUInt32LE(eocd + 16);
  const files = new Map();
  for (let index = 0; index < entryCount; index += 1) {
    expect(archive.readUInt32LE(offset)).toBe(0x02014b50);
    const method = archive.readUInt16LE(offset + 10);
    const compressedSize = archive.readUInt32LE(offset + 20);
    const nameLength = archive.readUInt16LE(offset + 28);
    const extraLength = archive.readUInt16LE(offset + 30);
    const commentLength = archive.readUInt16LE(offset + 32);
    const localOffset = archive.readUInt32LE(offset + 42);
    const name = archive.subarray(offset + 46, offset + 46 + nameLength).toString("utf8");
    expect(name.startsWith("/") || name.includes("\\") || name.split("/").includes("..")).toBe(false);
    expect(archive.readUInt32LE(localOffset)).toBe(0x04034b50);
    const localNameLength = archive.readUInt16LE(localOffset + 26);
    const localExtraLength = archive.readUInt16LE(localOffset + 28);
    const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = archive.subarray(dataOffset, dataOffset + compressedSize);
    files.set(name, method === 0 ? compressed : inflateRawSync(compressed));
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return files;
}

async function finishWinningSession(request, token, projectUuid, version) {
  const config = JSON.parse(version.gameConfig);
  const target = Number(config.objectives.targetCollectibles);
  const itemIds = config.entities.collectibles.slice(0, target).map((item) => item.id);
  expect(itemIds.length).toBe(target);
  const session = await call(request, "POST", `/api/projects/${projectUuid}/prototype-versions/${version.versionUuid}/playtest-sessions`, { token, body: {} });
  const events = [event(1, "SESSION_STARTED", 0)];
  itemIds.forEach((itemId, index) => events.push(event(index + 2, "ITEM_COLLECTED", 1000 + index * 500, { itemId })));
  const wonAt = 1000 + target * 500;
  events.push(event(events.length + 1, "GAME_WON", wonAt));
  events.push(event(events.length + 1, "SESSION_ENDED", wonAt + 100, { reason: "COMPLETED" }));
  const result = await call(request, "POST", `/api/projects/${projectUuid}/playtest-sessions/${session.sessionUuid}/events`, {
    token,
    body: { batchUuid: crypto.randomUUID(), events }
  });
  expect(result.session.status).toBe("ENDED");
  expect(result.session.outcome).toBe("WON");
}

test("V3 main chain generates, tunes, evaluates and exports a self-contained package", async ({ page, request }) => {
  const suffix = `${Date.now().toString(36)}${crypto.randomBytes(2).toString("hex")}`.slice(-12);
  const username = `v3e2e${suffix}`.slice(0, 20);
  const loginSecret = crypto.randomBytes(18).toString("base64url");
  await call(request, "POST", "/api/auth/register", { body: { username, password: loginSecret } });
  const login = await call(request, "POST", "/api/auth/login", { body: { username, password: loginSecret } });
  const token = login.token;

  const project = await call(request, "POST", "/api/projects", {
    token,
    body: { name: `V3 Release ${suffix}`, gameType: "arcade_collect", targetPlatform: "H5", description: "V3 export release acceptance" }
  });
  const submitted = await call(request, "POST", `/api/v1/projects/${project.projectUuid}/workflow-runs`, {
    token,
    key: `generate-${suffix}`,
    body: {
      workflowKey: "GAME_GENERATE",
      idea: "在霓虹博物馆中收集能量碎片并避开巡逻者，抵达出口。",
      durationSeconds: 90,
      difficulty: "normal",
      visualTheme: "neon museum",
      additionalRequirements: "键盘与触屏均可操作，保持轻量离线运行。",
      context: "V3 release acceptance"
    }
  });

  let run;
  await expect.poll(async () => {
    run = await call(request, "GET", `/api/v1/workflow-runs/${submitted.workflowRunUuid}`, { token });
    if (["FAILED", "TIMEOUT", "CANCELED"].includes(run.status)) throw new Error(`workflow ended as ${run.status}: ${JSON.stringify(run.error)}`);
    return run.status;
  }, { timeout: 360_000, intervals: [500, 1000, 2000, 5000] }).toBe("SUCCESS");
  expect(run.artifacts.map((artifact) => artifact.type)).toEqual(expect.arrayContaining(["GAME_CONCEPT_RESULT", "CORE_LOOP_DESIGN_RESULT", "TASK_BREAKDOWN_RESULT", "GAME_CONFIG", "RESOURCE_MANIFEST"]));

  let versions = await call(request, "GET", `/api/projects/${project.projectUuid}/prototype-versions`, { token });
  expect(versions).toHaveLength(1);
  const version1 = await call(request, "GET", `/api/projects/${project.projectUuid}/prototype-versions/${versions[0].versionUuid}`, { token });
  for (let index = 0; index < 5; index += 1) await finishWinningSession(request, token, project.projectUuid, version1);

  const speed = Number(version1.parameters.playerSpeed);
  const version2 = await call(request, "POST", `/api/projects/${project.projectUuid}/prototype-versions/${version1.versionUuid}/tune`, {
    token,
    key: `tune-${suffix}`,
    body: { playerSpeed: speed >= 400 ? speed - 10 : speed + 10 }
  });
  expect(version2.parentVersionUuid).toBe(version1.versionUuid);
  for (let index = 0; index < 5; index += 1) await finishWinningSession(request, token, project.projectUuid, version2);

  const metrics = await call(request, "GET", `/api/projects/${project.projectUuid}/prototype-versions/${version2.versionUuid}/playtest-metrics`, { token });
  expect(metrics.sampleSize).toBe(5);
  expect(metrics.sufficientForAi).toBe(true);
  const suggestion = await call(request, "POST", `/api/projects/${project.projectUuid}/prototype-versions/${version2.versionUuid}/balance-suggestions`, {
    token, key: `suggest-${suffix}`, body: {}
  });
  expect(suggestion.prototypeVersionUuid).toBe(version2.versionUuid);

  const exportJob = await call(request, "POST", `/api/projects/${project.projectUuid}/prototype-versions/${version2.versionUuid}/exports`, {
    token, key: `export-${suffix}`, body: {}
  });
  expect(exportJob.status).toBe("COMPLETED");
  expect(exportJob.packageDigest).toMatch(/^[0-9a-f]{64}$/);
  const replay = await call(request, "POST", `/api/projects/${project.projectUuid}/prototype-versions/${version2.versionUuid}/exports`, {
    token, key: `export-${suffix}`, body: {}
  });
  expect(replay.jobUuid).toBe(exportJob.jobUuid);
  expect(replay.reused).toBe(true);

  const download = await request.get(`${apiBase}/api/projects/${project.projectUuid}/exports/${exportJob.jobUuid}/download`, { headers: { Authorization: `Bearer ${token}` } });
  expect(download.ok()).toBeTruthy();
  expect(download.headers()["content-type"]).toContain("application/zip");
  const archive = await download.body();
  expect(archive.subarray(0, 4).toString("hex")).toBe("504b0304");
  expect(crypto.createHash("sha256").update(archive).digest("hex")).toBe(exportJob.packageDigest);

  const packageFiles = unzip(archive);
  expect([...packageFiles.keys()]).toEqual(expect.arrayContaining(["demo/index.html", "demo/game-config.js", "demo/runtime.js", "manifest.json"]));
  const offlineRoot = await fs.mkdtemp(path.join(os.tmpdir(), "v3-prototype-"));
  try {
    for (const [name, content] of packageFiles) {
      const target = path.join(offlineRoot, ...name.split("/"));
      await fs.mkdir(path.dirname(target), { recursive: true });
      await fs.writeFile(target, content);
    }
    await page.goto(pathToFileURL(path.join(offlineRoot, "demo", "index.html")).href);
    await expect(page.locator("#game")).toBeVisible();
    await expect(page.locator("#hud")).toContainText("READY");
    await page.locator("#start").click();
    await expect(page.locator("#hud")).toContainText("PLAYING");
  } finally {
    await fs.rm(offlineRoot, { recursive: true, force: true });
  }

  await page.addInitScript(({ token }) => sessionStorage.setItem("gameflow.session", token), { token });
  await page.goto(`/projects/${project.projectUuid}/versions`);
  await expect(page.getByRole("heading", { name: "原型版本与调参" })).toBeVisible();
  await expect(page.getByRole("button", { name: /版本 2/ })).toBeVisible();
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true", { timeout: 20_000 });
  await expect(page.getByText("结束样本").locator(".." )).toContainText("5");

  await page.setViewportSize({ width: 375, height: 812 });
  await expect(page.getByRole("heading", { name: "原型版本与调参" })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});
