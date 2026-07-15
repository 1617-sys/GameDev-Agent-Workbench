import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  testMatch: "v3-main.browser.e2e.js",
  timeout: 480_000,
  expect: { timeout: 20_000 },
  retries: 0,
  workers: 1,
  use: {
    baseURL: process.env.E2E_FRONTEND_BASE_URL || "http://127.0.0.1:5173",
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  },
  reporter: [["list"]]
});
