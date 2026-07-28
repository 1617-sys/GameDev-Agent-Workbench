import { test, expect } from "@playwright/test";

const project = "11111111-1111-4111-8111-111111111111";
const run = "22222222-2222-4222-8222-222222222222";
const version = "33333333-3333-4333-8333-333333333333";
const persisted = {
  run: {
    runUuid: run, status: "WAITING_APPROVAL", stateVersion: 4, executionAttempt: 2,
    goalDigest: "a".repeat(64),
    budgetJson: JSON.stringify({ maxRounds: 4, maxToolCalls: 12, maxCandidates: 3, maxEpisodes: 9, maxTokens: 20000, maxCostMicros: 2000000, maxWallClockMs: 900000, maxFailures: 3 }),
    checkpointJson: JSON.stringify({ usage: { rounds: 2, toolCalls: 11, candidates: 2, episodes: 6 } }),
    waitingApprovalRef: `approval://${version}`, createdAt: "2026-07-28T10:00:00Z"
  },
  decisions: Array.from({ length: 12 }, (_, i) => ({ decisionUuid: `d${i}`, roundNumber: i + 1, kind: "CALL_TOOL", reasonSummary: `decision ${i + 1}`, decisionDigest: String(i).padStart(64, "0"), createdAt: "2026-07-28T10:00:00Z" })),
  toolCalls: Array.from({ length: 12 }, (_, i) => ({ callUuid: `c${i}`, toolName: "RUN_PLAYER_EXPERIMENT", toolVersion: "1.0", status: "SUCCEEDED", inputDigest: "b".repeat(64), outputDigest: "c".repeat(64), durationMs: 10, retryCount: 0, outputSummary: `call ${i + 1}` })),
  candidates: [{ candidateUuid: "candidate-1", ordinalNumber: 1, status: "EVALUATED", generatorVersion: "deterministic-neighbor/1.0", configDigest: "d".repeat(64), prototypeVersionUuid: version, tuningJson: '{"playerSpeed":240}', evidenceJson: '{"personaMetrics":{"NOVICE_COMPLETION_RATE":0.8}}' }]
};

test("Director evidence restores, paginates and approves on desktop and narrow screens", async ({ page }) => {
  let approvals = 0;
  await page.addInitScript(() => sessionStorage.setItem("gameflow.session", "test-token"));
  await page.route("**/api/auth/me", route => route.fulfill({ json: { code: 0, data: { username: "director-reviewer" } } }));
  await page.route("**/api/projects", route => route.fulfill({ json: { code: 0, data: [] } }));
  await page.route(`**/api/projects/${project}/director-runs/${run}`, route => route.fulfill({ json: { code: 0, data: persisted } }));
  await page.route(`**/api/projects/${project}/prototype-versions/${version}/approval`, async route => {
    approvals += 1;
    expect(await route.request().postDataJSON()).toMatchObject({ decision: "APPROVED" });
    expect(route.request().headers()["idempotency-key"]).toBeTruthy();
    await route.fulfill({ json: { code: 0, data: { decision: "APPROVED", reused: approvals > 1 } } });
  });
  await page.goto(`/projects/${project}/director-runs/${run}`);
  await expect(page.getByRole("heading", { name: "实验导演工作台" })).toBeVisible();
  await expect(page.getByText("WAITING_APPROVAL")).toBeVisible();
  await expect(page.getByText("decision 11")).toBeHidden();
  await page.getByRole("button", { name: "下一页" }).first().click();
  await expect(page.getByText("decision 11")).toBeVisible();
  await page.getByLabel("审批理由").fill("证据满足目标和保护约束");
  await page.getByRole("button", { name: "批准 DRAFT" }).click();
  await expect.poll(() => approvals).toBe(1);
  await page.setViewportSize({ width: 375, height: 812 });
  await expect(page.getByRole("heading", { name: "实验导演工作台" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(0);
});
