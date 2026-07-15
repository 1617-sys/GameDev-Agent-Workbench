import { test, expect } from "@playwright/test";

test("standalone Phaser demo renders a playable canvas", async ({ page }) => {
  await page.goto("/demo/play");
  await expect(page.getByRole("heading", { name: "博物馆夺宝", level: 1 })).toBeVisible();
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready", "true", { timeout: 15_000 });
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-engine", "Phaser 3");
  await expect(page.locator(".game-canvas")).toHaveAttribute("data-physics", "arcade");
  const canvas = page.locator(".game-canvas canvas");
  await expect(canvas).toBeVisible();
  const box = await canvas.boundingBox();
  expect(box?.width).toBeGreaterThan(300);
  expect(box?.height).toBeGreaterThan(160);
});
