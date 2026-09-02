import { test, expect } from "@playwright/test";

const projectUuid = "compat-project";
const project = { projectUuid, name: "V4/V5 兼容项目" };
const capabilities = ["projects.read", "generation.read", "prototype-versions.read", "prototype-versions.manage"];

async function authenticatedProject(page) {
  await page.addInitScript(() => sessionStorage.setItem("gameflow.session", "compat-token"));
  await page.route("**/api/auth/me", route => route.fulfill({ json: { code: 0, data: { username: "compat-tester", capabilities } } }));
  await page.route("**/api/projects", route => route.fulfill({ json: { code: 0, data: [project] } }));
  await page.route(`**/api/projects/${projectUuid}`, route => route.fulfill({ json: { code: 0, data: project } }));
}

test("V4 prototype-version surface remains reachable", async ({ page }) => {
  await authenticatedProject(page);
  await page.route(`**/api/projects/${projectUuid}/prototype-versions`, route => route.fulfill({ json: { code: 0, data: [] } }));
  await page.goto(`/projects/${projectUuid}/versions`);
  await expect(page.getByRole("heading", { name: "原型版本与调参" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "还没有原型版本" })).toBeVisible();
});

test("V5 Cocos generation surface retains the 3.8.8 build and release pipeline", async ({ page }) => {
  await authenticatedProject(page);
  await page.route("**/api/v5/gamespec/capabilities", route => route.fulfill({ json: { code: 0, data: { version: "arcade_collect/1", digest: "a".repeat(64), kinds: ["arcade_collect"] } } }));
  await page.goto(`/projects/${projectUuid}/studio`);
  await expect(page.getByRole("heading", { name: project.name })).toBeVisible();
  await expect(page.getByText("Cocos Web Mobile 构建", { exact: true })).toBeVisible();
  await expect(page.getByText("人工审批", { exact: true })).toBeVisible();
  await expect(page.getByText("正式发布", { exact: true })).toBeVisible();
  await expect(page.getByText("3.8.8", { exact: true })).toBeVisible();
});
