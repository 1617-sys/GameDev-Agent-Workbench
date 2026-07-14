import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const enabled = process.env.RUN_MAIN_WORKFLOW_E2E === "1";
const apiBaseUrl = process.env.E2E_API_BASE_URL || "http://127.0.0.1:8080";
const frontendBaseUrl = process.env.E2E_FRONTEND_BASE_URL || "http://127.0.0.1:5173";
const fixtureUsername = process.env.E2E_FIXTURE_USERNAME || "";
const mysqlContainer = process.env.E2E_MYSQL_CONTAINER || "";
const evidenceDir = process.env.E2E_EVIDENCE_DIR || path.join(process.cwd(), "test-results", "main-workflow-e2e");
const fixtureDir = path.join(path.dirname(fileURLToPath(import.meta.url)), "../../tools/e2e/fixtures");
const evidence = {
  providerMode: "fake",
  fixtureVersion: "r7-e2e-fixed-agent-v1",
  apiBaseUrl,
  frontendBaseUrl,
  fixtureUsername,
  scenarios: [],
  contractFailures: []
};

function writeEvidence(name, value) {
  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(path.join(evidenceDir, name), JSON.stringify(value, null, 2) + "\n", "utf8");
}

function check(condition, message) {
  if (!condition) evidence.contractFailures.push(message);
}

function shellQuote(value) {
  return `'${value.replaceAll("'", "'\"'\"'")}'`;
}

function mysql(sql) {
  if (!mysqlContainer) throw new Error("E2E_MYSQL_CONTAINER is required for the database correlation assertion.");
  const command = `MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --skip-column-names -uroot -D "$MYSQL_DATABASE" -e ${shellQuote(sql)}`;
  return execFileSync("docker", ["exec", "-i", mysqlContainer, "sh", "-lc", command], { encoding: "utf8" })
    .trim()
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => line.split("\t"));
}

async function api(pathname, { token = "", method = "GET", body, form, requestHeaders = {} } = {}) {
  const headers = { Accept: "application/json", ...requestHeaders };
  if (token) headers.Authorization = `Bearer ${token}`;
  let requestBody;
  if (form) {
    requestBody = form;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    requestBody = JSON.stringify(body);
  }
  const response = await fetch(`${apiBaseUrl}${pathname}`, { method, headers, body: requestBody });
  const payload = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok || payload?.code !== 0) {
    throw new Error(`${method} ${pathname} failed: HTTP ${response.status} ${payload?.message || "unexpected response"}`);
  }
  return payload?.data ?? payload;
}

async function pollUntil(label, condition, timeoutMs = 115_000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await condition();
    if (last) return last;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not complete within ${timeoutMs}ms.`);
}

async function registerAndCreateProject(suffix) {
  const username = `${fixtureUsername}${suffix}`.slice(0, 20);
  const password = `R7e2e-${fixtureUsername.slice(-8)}-A1`;
  await api("/api/auth/register", { method: "POST", body: { username, password } });
  const login = await api("/api/auth/login", { method: "POST", body: { username, password } });
  const project = await api("/api/projects", {
    token: login.token,
    method: "POST",
    body: {
      name: `R7 E2E ${suffix}`,
      gameType: "top_down_collect",
      targetPlatform: "web",
      description: "Controlled R7 E2E fixture."
    }
  });
  return {
    username,
    password,
    token: login.token,
    projectUuid: project.projectUuid,
    projectName: project.name
  };
}

async function uploadAndWaitForReady(fixture, filename, fixturePath) {
  const form = new FormData();
  form.set("file", new Blob([readFileSync(fixturePath)], { type: "text/markdown" }), filename);
  const upload = await api(`/api/projects/${encodeURIComponent(fixture.projectUuid)}/knowledge-documents`, {
    token: fixture.token,
    method: "POST",
    form
  });
  const document = await pollUntil(`knowledge document ${upload.documentUuid}`, async () => {
    const library = await api(`/api/projects/${encodeURIComponent(fixture.projectUuid)}/knowledge-documents`, { token: fixture.token });
    return library.documents?.find((item) => item.documentUuid === upload.documentUuid && item.status === "READY") || null;
  });
  return { documentUuid: upload.documentUuid, status: document.status };
}

async function waitForRun(token, workflowRunUuid) {
  return pollUntil(`workflow ${workflowRunUuid}`, async () => {
    const run = await api(`/api/v1/workflow-runs/${encodeURIComponent(workflowRunUuid)}`, { token });
    return ["SUCCESS", "FAILED", "TIMEOUT", "CANCELED"].includes(run.status) ? run : null;
  });
}

function parseSse(text) {
  return text.split(/\r?\n\r?\n/).map((frame) => {
    const values = Object.fromEntries(frame.split(/\r?\n/)
      .filter((line) => line.includes(":"))
      .map((line) => {
        const separator = line.indexOf(":");
        return [line.slice(0, separator), line.slice(separator + 1).trimStart()];
      }));
    return values.data ? { id: values.id || null, event: values.event || "message", data: JSON.parse(values.data) } : null;
  }).filter(Boolean);
}

async function replaySse(token, workflowRunUuid) {
  const response = await fetch(`${apiBaseUrl}/api/v1/workflow-runs/${encodeURIComponent(workflowRunUuid)}/events`, {
    headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}`, "Last-Event-ID": "0" }
  });
  if (!response.ok) throw new Error(`SSE replay failed for ${workflowRunUuid}: HTTP ${response.status}`);
  return parseSse(await response.text());
}

async function submitWorkflowFromUi(page, fixture, idea, sequence) {
  const startedAt = Date.now();
  const eventRequests = [];
  const handler = (request) => {
    if (new URL(request.url()).pathname.endsWith("/events")) eventRequests.push(request.url());
  };
  page.on("request", handler);
  try {
    await page.goto(frontendBaseUrl);
    await page.getByPlaceholder("用户名").fill(fixture.username);
    await page.getByPlaceholder("密码").fill(fixture.password);
    await page.getByRole("button", { name: "登录" }).click();
    await expect(page.getByRole("button", { name: fixture.projectName })).toBeVisible({ timeout: 15_000 });
    await page.getByRole("button", { name: fixture.projectName }).click();
    await expect(page.locator("form.workflow-form")).toBeVisible({ timeout: 15_000 });

    await page.getByLabel("游戏想法").fill(idea);
    const workflowResponse = page.waitForResponse((response) =>
      response.request().method() === "POST" &&
      new URL(response.url()).pathname.endsWith(`/projects/${fixture.projectUuid}/workflow-runs`)
    );
    await page.getByRole("button", { name: "开始生成" }).click();
    const response = await workflowResponse;
    const payload = await response.json();
    check(response.status() === 202, `UI submit ${sequence} did not return HTTP 202.`);
    check(Boolean(payload?.data?.workflowRunUuid), `UI submit ${sequence} did not return workflowRunUuid.`);
    const workflowRunUuid = payload?.data?.workflowRunUuid;
    evidence.scenarios.push({
      name: `legal-workflow-${sequence}-submit`,
      httpStatus: response.status(),
      responseCode: payload?.code ?? null,
      responseMessage: payload?.message || null,
      workflowRunUuid: workflowRunUuid || null
    });
    if (!workflowRunUuid) {
      throw new Error(`UI submit ${sequence} did not create a WorkflowRun (HTTP ${response.status()}, code ${payload?.code ?? "missing"}, message ${payload?.message || "missing"}).`);
    }
    await expect(page).toHaveURL(new RegExp(`/workflow-runs/${workflowRunUuid}$`), { timeout: 15_000 });
    await expect(page.locator(".run-summary h2")).toHaveText("SUCCESS", { timeout: 115_000 });
    await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
    const beforeRefreshSteps = await page.locator(".run-steps li").allTextContents();
    await page.reload();
    await expect(page.locator(".run-summary h2")).toHaveText("SUCCESS", { timeout: 15_000 });
    await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
    const afterRefreshSteps = await page.locator(".run-steps li").allTextContents();
    check(beforeRefreshSteps.length === 4 && afterRefreshSteps.length === 4, `UI refresh ${sequence} did not preserve exactly four workflow steps.`);
    check(JSON.stringify(beforeRefreshSteps) === JSON.stringify(afterRefreshSteps), `UI refresh ${sequence} changed or duplicated persisted workflow steps.`);
    check(eventRequests.length >= 1, `UI submit ${sequence} did not open the workflow SSE endpoint.`);
    await page.getByRole("button", { name: "退出登录" }).click();
    await expect(page.getByPlaceholder("用户名")).toBeVisible();
    return { workflowRunUuid, elapsedMs: Date.now() - startedAt, eventConnections: eventRequests.length };
  } finally {
    page.off("request", handler);
  }
}

test.describe("main workflow Compose E2E", () => {
  test.skip(!enabled, "Set RUN_MAIN_WORKFLOW_E2E=1 and use tools/e2e/Invoke-R7MainWorkflowE2E.ps1.");

  test("proves GAME_GENERATE from visible submission through Phaser preview", async ({ page }) => {
    if (!/^[a-z0-9-]{4,15}$/.test(fixtureUsername)) throw new Error("E2E_FIXTURE_USERNAME must be a short controlled fixture username.");
    try {
      const fixture = await registerAndCreateProject("main");
      const result = await submitWorkflowFromUi(page, fixture, "Crystal Relay game generation", 1);
      const run = await waitForRun(fixture.token, result.workflowRunUuid);
      const artifacts = await api(`/api/v1/workflow-runs/${result.workflowRunUuid}/artifacts`, { token: fixture.token });
      const gameConfigArtifact = artifacts.find((artifact) => artifact.type === "GAME_CONFIG");
      const definitions = mysql("select status, json_length(definition_json, '$.steps') from workflow_definition_version where workflow_key = 'GAME_GENERATE' and status = 'ACTIVE';");

      check(run.status === "SUCCESS", "GAME_GENERATE did not reach SUCCESS.");
      check(result.elapsedMs <= 120_000, `Login-to-Phaser-ready chain exceeded 120 seconds (${result.elapsedMs}ms).`);
      check(definitions.length === 1 && definitions[0][0] === "ACTIVE" && Number(definitions[0][1]) === 4,
        "The clean database does not provide one active four-step GAME_GENERATE definition.");
      check(Boolean(gameConfigArtifact?.artifactUuid) && gameConfigArtifact?.url?.startsWith("/api/artifacts/"),
        `GAME_CONFIG artifact ${gameConfigArtifact?.artifactUuid || "missing"} is not exposed through the authenticated artifact API.`);
      evidence.scenarios.push({ name: "game-generate-main-flow", workflowRunUuid: result.workflowRunUuid, elapsedMs: result.elapsedMs, status: run.status, providerMode: "fake" });
      evidence.phaserRuntime = { ready: true, renderedInWorkflowDetail: true };
      expect(evidence.contractFailures, "main workflow release contract failures").toEqual([]);
    } finally {
      evidence.completedAtUtc = new Date().toISOString();
      writeEvidence("sanitized-client-trace.json", evidence);
    }
  });
});
