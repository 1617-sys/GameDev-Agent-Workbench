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
  const command = `mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -e ${shellQuote(sql)}`;
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
  return { username, password, token: login.token, projectUuid: project.projectUuid };
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
    await page.locator(".login-grid input").nth(0).fill(fixture.username);
    await page.locator(".login-grid input").nth(1).fill(fixture.password);
    await page.locator(".login-grid button").click();
    await expect(page.locator("form.workflow-form")).toBeVisible({ timeout: 15_000 });

    const fields = page.locator("form.workflow-form input");
    await fields.nth(0).fill(fixture.projectUuid);
    await fields.nth(1).fill("DEMO_GAME_CONFIG");
    await page.locator("form.workflow-form textarea").nth(0).fill(idea);
    const workflowResponse = page.waitForResponse((response) =>
      response.request().method() === "POST" &&
      new URL(response.url()).pathname.endsWith(`/projects/${fixture.projectUuid}/workflow-runs`)
    );
    await page.locator("form.workflow-form button.primary-button").click();
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
      await page.screenshot({ path: path.join(evidenceDir, `workflow-submit-${sequence}-failed.png`), fullPage: true });
      throw new Error(`UI submit ${sequence} did not create a WorkflowRun (HTTP ${response.status()}, code ${payload?.code ?? "missing"}, message ${payload?.message || "missing"}).`);
    }
    await expect(page).toHaveURL(new RegExp(`/workflow-runs/${workflowRunUuid}$`), { timeout: 15_000 });
    await expect(page.locator(".run-summary h2")).toHaveText("SUCCESS", { timeout: 115_000 });
    const beforeRefreshSteps = await page.locator(".run-steps li").allTextContents();
    await page.screenshot({ path: path.join(evidenceDir, `workflow-${sequence}.png`), fullPage: true });
    await page.reload();
    await expect(page.locator(".run-summary h2")).toHaveText("SUCCESS", { timeout: 15_000 });
    const afterRefreshSteps = await page.locator(".run-steps li").allTextContents();
    check(beforeRefreshSteps.length === 4 && afterRefreshSteps.length === 4, `UI refresh ${sequence} did not preserve exactly four workflow steps.`);
    check(JSON.stringify(beforeRefreshSteps) === JSON.stringify(afterRefreshSteps), `UI refresh ${sequence} changed or duplicated persisted workflow steps.`);
    check(eventRequests.length >= 1, `UI submit ${sequence} did not open the workflow SSE endpoint.`);
    return { workflowRunUuid, elapsedMs: Date.now() - startedAt, eventConnections: eventRequests.length };
  } finally {
    page.off("request", handler);
  }
}

function inList(values) {
  return values.map((value) => `'${value.replaceAll("'", "''")}'`).join(",");
}

test.describe("R7 main workflow Compose E2E", () => {
  test.skip(!enabled, "Set RUN_MAIN_WORKFLOW_E2E=1 and use tools/e2e/Invoke-R7MainWorkflowE2E.ps1.");

  test("proves the full user chain and reports every contract break with redacted evidence", async ({ page }) => {
    if (!/^[a-z0-9-]{4,15}$/.test(fixtureUsername)) throw new Error("E2E_FIXTURE_USERNAME must be a short controlled fixture username.");
    try {
      const validRuns = [];
      for (let sequence = 1; sequence <= 3; sequence += 1) {
        const fixture = await registerAndCreateProject(`v${sequence}`);
        const result = await submitWorkflowFromUi(page, fixture, `R7 legal Crystal Relay workflow ${sequence}`, sequence);
        const run = await waitForRun(fixture.token, result.workflowRunUuid);
        check(run.status === "SUCCESS", `Legal workflow ${sequence} did not reach SUCCESS.`);
        check(result.elapsedMs <= 120_000, `Login-to-Phaser-ready chain ${sequence} exceeded 120 seconds (${result.elapsedMs}ms).`);
        validRuns.push({ ...result, projectUuid: fixture.projectUuid, status: run.status, token: fixture.token });
        evidence.scenarios.push({ name: `legal-workflow-${sequence}`, workflowRunUuid: result.workflowRunUuid, elapsedMs: result.elapsedMs, status: run.status, providerMode: "fake" });
      }

      const invalidFixture = await registerAndCreateProject("invalid");
      const invalidSubmit = await api(`/api/v1/projects/${invalidFixture.projectUuid}/workflow-runs`, {
        token: invalidFixture.token,
        method: "POST",
        requestHeaders: { "Idempotency-Key": `invalid-${fixtureUsername}` },
        body: { workflowKey: "DEMO_GAME_CONFIG", idea: "E2E_INVALID_CONFIG", context: "controlled negative fixture" }
      });
      const invalidRun = await waitForRun(invalidFixture.token, invalidSubmit.workflowRunUuid);
      const invalidArtifacts = await api(`/api/v1/workflow-runs/${invalidSubmit.workflowRunUuid}/artifacts`, { token: invalidFixture.token });
      check(invalidRun.status === "FAILED", "Illegal GameConfig was not blocked by the workflow evaluation gate.");
      check(!invalidArtifacts.some((artifact) => artifact.type === "GAME_CONFIG"), "Illegal GameConfig produced an available GAME_CONFIG artifact.");
      evidence.scenarios.push({ name: "illegal-game-config", workflowRunUuid: invalidSubmit.workflowRunUuid, status: invalidRun.status });

      const ragOnFixture = await registerAndCreateProject("ragon");
      const ragOnDocument = await uploadAndWaitForReady(ragOnFixture, "r7-rag-on.md", path.join(fixtureDir, "rag-on.md"));
      const ragOn = await api("/api/agent/run", {
        token: ragOnFixture.token,
        method: "POST",
        body: {
          projectUuid: ragOnFixture.projectUuid,
          agentType: "GAME_CONCEPT",
          title: "R7 RAG-on fixture",
          content: "crystal relay signal key",
          context: "controlled fixture",
          ragEnabled: true,
          ragTopK: 3
        }
      });

      const ragOffFixture = await registerAndCreateProject("ragoff");
      const ragOffDocument = await uploadAndWaitForReady(ragOffFixture, "r7-rag-off.md", path.join(fixtureDir, "rag-off.md"));
      const ragOff = await api("/api/agent/run", {
        token: ragOffFixture.token,
        method: "POST",
        body: {
          projectUuid: ragOffFixture.projectUuid,
          agentType: "GAME_CONCEPT",
          title: "R7 RAG-off fixture",
          content: "crystal relay signal key",
          context: "controlled fixture",
          ragEnabled: false
        }
      });
      check(ragOn.mockState === "TRUE" && ragOff.mockState === "TRUE", "Controlled fake Agent was not explicitly persisted as mock.");
      evidence.scenarios.push({ name: "rag-on", agentRunUuid: ragOn.runUuid, documentUuid: ragOnDocument.documentUuid, providerMode: "fake" });
      evidence.scenarios.push({ name: "rag-off", agentRunUuid: ragOff.runUuid, documentUuid: ragOffDocument.documentUuid, providerMode: "fake" });

      const workflowIds = validRuns.map((item) => item.workflowRunUuid);
      const workflowRows = mysql(`
        select wr.workflow_run_uuid, coalesce(wr.trace_id, ''), wr.status,
               count(distinct ws.id),
               count(distinct case when ws.status = 'SUCCESS' then ws.id end),
               count(distinct case when ws.agent_run_id is not null then ws.id end),
               count(distinct ar.id),
               count(distinct case when ar.workflow_run_id = wr.id then ar.id end),
               count(distinct case when ar.step_run_id = ws.id then ar.id end),
               count(distinct metric.id),
               count(distinct case when metric.workflow_run_id = wr.id then metric.id end),
               count(distinct artifact.id),
               count(distinct case when artifact.artifact_type = 'GAME_CONFIG' then artifact.id end),
               count(distinct case when evaluation.evaluator_type = 'RUNTIME' and evaluation.status = 'PASS' then evaluation.id end),
               coalesce(group_concat(distinct concat(evaluation.evaluator_type, ':', evaluation.status) order by evaluation.evaluator_type separator ','), '')
        from workflow_run wr
        left join workflow_step_run ws on ws.workflow_run_id = wr.id
        left join agent_run ar on ar.id = ws.agent_run_id
        left join model_call_metric metric on metric.agent_run_id = ar.id
        left join agent_artifact artifact on artifact.step_run_id = ws.id
        left join evaluation_report evaluation on evaluation.artifact_id = artifact.id
        where wr.workflow_run_uuid in (${inList(workflowIds)})
        group by wr.id, wr.workflow_run_uuid, wr.trace_id, wr.status
        order by wr.workflow_run_uuid;`);
      const ragRows = mysql(`
        select ar.run_uuid, ar.rag_enabled, ar.rag_status, ar.mock_state, count(rr.id)
        from agent_run ar
        left join retrieval_record rr on rr.agent_run_id = ar.id
        where ar.run_uuid in (${inList([ragOn.runUuid, ragOff.runUuid])})
        group by ar.id, ar.run_uuid, ar.rag_enabled, ar.rag_status, ar.mock_state
        order by ar.run_uuid;`);
      evidence.database = {
        workflowCorrelation: workflowRows.map((row) => ({
          workflowRunUuid: row[0], traceIdPresent: Boolean(row[1]), status: row[2], stepCount: Number(row[3]),
          successfulSteps: Number(row[4]), linkedSteps: Number(row[5]), agentRuns: Number(row[6]),
          agentWorkflowLinks: Number(row[7]), agentStepLinks: Number(row[8]), metrics: Number(row[9]),
          metricWorkflowLinks: Number(row[10]), artifacts: Number(row[11]), gameConfigArtifacts: Number(row[12]),
          runtimePassReports: Number(row[13]), evaluationStates: row[14]
        })),
        rag: ragRows.map((row) => ({ agentRunUuid: row[0], ragEnabled: Number(row[1]), ragStatus: row[2], mockState: row[3], retrievalRecords: Number(row[4]) }))
      };

      for (const row of evidence.database.workflowCorrelation) {
        check(row.traceIdPresent, `Workflow ${row.workflowRunUuid} has no persisted traceId.`);
        check(row.stepCount === 4 && row.successfulSteps === 4 && row.linkedSteps === 4, `Workflow ${row.workflowRunUuid} does not have four successful linked StepRuns.`);
        check(row.agentRuns === 4 && row.metrics === 4 && row.artifacts === 4, `Workflow ${row.workflowRunUuid} does not have one AgentRun, Metric, and Artifact per step.`);
        check(row.agentWorkflowLinks === 4 && row.agentStepLinks === 4, `R2: AgentRun workflow_run_id/step_run_id are not persisted for workflow ${row.workflowRunUuid}.`);
        check(row.metricWorkflowLinks === 4, `R2: ModelCallMetric workflow_run_id is not persisted for workflow ${row.workflowRunUuid}.`);
        check(row.gameConfigArtifacts === 1, `Workflow ${row.workflowRunUuid} does not expose exactly one legal GAME_CONFIG artifact.`);
        check(row.runtimePassReports === 1, `R5: GameConfig runtime evaluation is not recorded as PASS for workflow ${row.workflowRunUuid} (${row.evaluationStates || "no evaluation reports"}).`);
      }

      const ragOnRow = evidence.database.rag.find((row) => row.agentRunUuid === ragOn.runUuid);
      const ragOffRow = evidence.database.rag.find((row) => row.agentRunUuid === ragOff.runUuid);
      check(ragOnRow?.ragEnabled === 1 && ragOnRow?.ragStatus === "AVAILABLE" && ragOnRow?.mockState === "TRUE" && ragOnRow?.retrievalRecords > 0,
        "RAG-on fixture did not persist an AVAILABLE mock retrieval record.");
      check(ragOffRow?.ragEnabled === 0 && ragOffRow?.ragStatus === "DISABLED" && ragOffRow?.mockState === "TRUE" && ragOffRow?.retrievalRecords === 0,
        "RAG-off fixture did not remain disabled with zero retrieval records.");

      const firstValid = validRuns[0];
      const artifacts = await api(`/api/v1/workflow-runs/${firstValid.workflowRunUuid}/artifacts`, { token: firstValid.token });
      const gameConfigArtifact = artifacts.find((artifact) => artifact.type === "GAME_CONFIG");
      check(gameConfigArtifact?.url?.startsWith("/demo/play"),
        `R2: GAME_CONFIG artifact ${gameConfigArtifact?.artifactUuid || "missing"} does not open Phaser Demo; its URL is ${gameConfigArtifact?.url || "missing"}.`);

      const workflowRagEvidence = await api(`/api/v1/workflow-runs/${firstValid.workflowRunUuid}/rag-evidence`, { token: firstValid.token });
      check(workflowRagEvidence.some((item) => item.ragEnabled === true && item.references?.length > 0),
        "R6: async WorkflowRun has no RAG-on StepRun evidence; its submission contract does not carry RAG settings into AgentRun.");

      const replay = await replaySse(firstValid.token, firstValid.workflowRunUuid);
      const snapshot = replay.find((event) => event.event === "snapshot")?.data;
      const incremental = replay.filter((event) => event.event !== "snapshot");
      const sequences = incremental.map((event) => Number(event.data.sequence));
      check(snapshot?.workflowRunUuid === firstValid.workflowRunUuid, "SSE reconnect snapshot does not identify the requested workflow run.");
      check(new Set(sequences).size === sequences.length, "SSE replay contains duplicate incremental sequence numbers.");
      check(sequences.every((sequence) => sequence <= Number(snapshot?.lastSequence)), "SSE replay sequence exceeds the persisted workflow snapshot.");
      evidence.sse = { workflowRunUuid: firstValid.workflowRunUuid, snapshotSequence: snapshot?.lastSequence ?? null, replayEventCount: incremental.length, uniqueIncrementalSequences: new Set(sequences).size };

      await page.goto(`${frontendBaseUrl}/demo/play`);
      await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
      await page.screenshot({ path: path.join(evidenceDir, "phaser-runtime.png"), fullPage: true });
      evidence.phaserRuntime = { ready: true, url: `${frontendBaseUrl}/demo/play` };
      if (evidence.contractFailures.length > 0) {
        await page.screenshot({ path: path.join(evidenceDir, "contract-blocked.png"), fullPage: true });
      }
      expect(evidence.contractFailures, "R7 release contract failures").toEqual([]);
    } finally {
      evidence.completedAtUtc = new Date().toISOString();
      writeEvidence("sanitized-client-trace.json", evidence);
    }
  });
});
