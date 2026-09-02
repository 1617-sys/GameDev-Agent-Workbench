import { test, expect } from "@playwright/test";

const project = { projectUuid: "project-role-e2e", name: "Role E2E" };
async function session(page, capabilities) {
  await page.addInitScript(() => sessionStorage.setItem("gameflow.session", "role-test-token"));
  await page.route("**/api/auth/me", route => route.fulfill({ json: { code: 0, data: { username: "role-tester", capabilities } } }));
  await page.route("**/api/projects", route => route.fulfill({ json: { code: 0, data: [project] } }));
}

test("ordinary user sees only ordinary navigation and admin URL/API escalation is rejected", async ({ page }) => {
  await session(page, ["projects.read", "generation.read", "generation.compile", "generation.build", "artifacts.read"]);
  await page.route("**/api/dashboard/projects/summary", route => route.fulfill({ status: 403, json: { code: 403, message: "Forbidden" } }));
  await page.goto("/projects");
  await expect(page.getByRole("link", { name: "项目中心" })).toBeVisible();
  await expect(page.getByText("管理员 / 诊断", { exact: true })).toHaveCount(0);
  await page.route(`**/api/projects/${project.projectUuid}`, route => route.fulfill({ json: { code: 0, data: project } }));
  await page.route("**/api/v5/gamespec/capabilities", route => route.fulfill({ json: { code: 0, data: { cocosCreatorVersion: "3.8.8" } } }));
  await page.goto(`/projects/${project.projectUuid}/studio`);
  await expect(page.getByRole("heading", { name: project.name })).toBeVisible();
  await expect(page.getByRole("button", { name: /生成 \/ 修复 GameSpec/ })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "开始创建游戏" })).toBeVisible();
  await expect(page.getByRole("button", { name: "开始创建游戏" })).toBeInViewport();
  await expect(page.getByRole("link", { name: "旧版创意工作流" })).toHaveCount(0);
  await page.goto("/admin/dashboard");
  await expect(page.getByRole("heading", { name: "没有访问权限" })).toBeVisible();
  const status = await page.evaluate(async () => (await fetch("http://127.0.0.1:8080/api/dashboard/projects/summary", { headers: { Authorization: "Bearer forged-admin" } })).status);
  expect(status).toBe(403);
});

test("project advanced user reaches project tools but not administrator pages", async ({ page }) => {
  await session(page, ["projects.read", "knowledge.read", "workflow-runs.manage", "player-runs.read", "prototype-versions.manage"]);
  await page.route(`**/api/projects/${project.projectUuid}/knowledge-documents`, route => route.fulfill({ json: { code: 0, data: { documents: [], capabilities: { upload: true } } } }));
  await page.goto(`/projects/${project.projectUuid}/knowledge`);
  await expect(page.getByRole("heading", { name: "项目知识库" })).toBeVisible();
  await expect(page.getByText("项目高级", { exact: true })).toBeVisible();
  await expect(page.getByText("管理员 / 诊断", { exact: true })).toHaveCount(0);
  await page.goto("/admin/prompt-ops");
  await expect(page.getByRole("heading", { name: "没有访问权限" })).toBeVisible();
});

test("administrator navigation exposes controlled operational pages", async ({ page }) => {
  await session(page, ["projects.read", "admin.dashboard", "admin.agent-runs", "prompt-ops.manage", "prompt-analytics.read", "admin.diagnostics"]);
  await page.route("**/api/dashboard/projects/summary", route => route.fulfill({ json: { code: 0, data: [] } }));
  await page.route("**/api/dashboard/projects/selectAgentType", route => route.fulfill({ json: { code: 0, data: [] } }));
  await page.goto("/admin/dashboard");
  await expect(page.getByRole("heading", { name: "运营总览" })).toBeVisible();
  await expect(page.getByText("管理员 / 诊断", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Agent Runs" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Prompt 运维" })).toBeVisible();
  await expect(page.getByRole("link", { name: "系统诊断" })).toBeVisible();
});
