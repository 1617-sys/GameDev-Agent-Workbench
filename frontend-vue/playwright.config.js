import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.browser.e2e.js",
  timeout: 30_000,
  retries: 0,
  use: {
    baseURL: process.env.E2E_FRONTEND_BASE_URL || "http://127.0.0.1:5173",
    screenshot: "only-on-failure",
    trace: "off"
  },
  reporter: [["list"]]
});
