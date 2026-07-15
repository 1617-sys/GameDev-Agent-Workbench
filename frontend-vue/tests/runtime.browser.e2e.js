import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  page.on("pageerror", (error) => console.error(`browser page error: ${error.stack || error.message}`));
});

async function move(page, key, durationMs) {
  await page.keyboard.down(key);
  await page.waitForTimeout(durationMs);
  await page.keyboard.up(key);
  await page.waitForTimeout(100);
}

test("desktop runtime starts, pauses, resumes and restarts without duplicating the scene", async ({ page }) => {
  await page.goto("/demo/play");
  await expect(page.getByRole("heading", { name: "博物馆夺宝", level: 1 })).toBeVisible();
  const runtime = page.locator(".game-canvas");
  await expect(runtime).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
  await expect(runtime).toHaveAttribute("data-runtime-state", "READY");
  await page.getByRole("button", { name: "开始游戏" }).click();
  await expect(runtime).toHaveAttribute("data-runtime-state", "PLAYING");
  await page.getByRole("button", { name: "暂停", exact: true }).click();
  await expect(runtime).toHaveAttribute("data-runtime-state", "PAUSED");
  await page.getByRole("button", { name: "继续游戏" }).click();
  await expect(runtime).toHaveAttribute("data-runtime-state", "PLAYING");
  await page.getByRole("button", { name: "重开", exact: true }).click();
  await expect(runtime).toHaveAttribute("data-runtime-state", "PLAYING");
  await expect(page.locator(".game-canvas canvas")).toHaveCount(1);
});

test("keyboard route collects the configured target, unlocks the exit and wins", async ({ page }) => {
  await page.goto("/demo/play");
  const runtime = page.locator(".game-canvas");
  await expect(runtime).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
  await page.getByRole("button", { name: "开始游戏" }).click();
  await move(page, "ArrowDown", 200);
  await move(page, "ArrowRight", 750);
  await expect(page.locator(".hud-stats")).toContainText("1/2");
  await move(page, "ArrowDown", 800);
  await move(page, "ArrowRight", 1180);
  await expect(page.locator(".hud-stats")).toContainText("2/2");
  await expect(page.locator(".exit-state")).toContainText("已解锁");
  await move(page, "ArrowDown", 610);
  await move(page, "ArrowRight", 1540);
  await expect(runtime).toHaveAttribute("data-runtime-state", "WON");
  await expect(page.getByText("挑战成功")).toBeVisible();
});

test("a failed sprite request falls back to geometry and keeps the demo playable", async ({ page }) => {
  await page.route("**/runtime-assets/player.svg", (route) => route.abort());
  await page.goto("/demo/play");
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
  await expect(page.locator(".resource-warning")).toContainText("内置几何占位");
  await expect(page.locator(".game-canvas canvas")).toBeVisible();
  await page.getByRole("button", { name: "开始游戏" }).click();
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-state", "PLAYING");
});

test("375px mobile viewport exposes stable touch controls without horizontal overflow", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/demo/play");
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
  for (const name of ["向上移动", "向下移动", "向左移动", "向右移动"]) {
    const button = page.getByRole("button", { name });
    await expect(button).toBeVisible();
    const box = await button.boundingBox();
    expect(box.width).toBeGreaterThanOrEqual(48);
    expect(box.height).toBeGreaterThanOrEqual(48);
  }
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.getByRole("button", { name: "开始游戏" }).click();
  await page.getByRole("button", { name: "向右移动" }).dispatchEvent("pointerdown", { pointerId: 1, pointerType: "touch" });
  await page.getByRole("button", { name: "向右移动" }).dispatchEvent("pointerup", { pointerId: 1, pointerType: "touch" });
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-state", "PLAYING");
});
