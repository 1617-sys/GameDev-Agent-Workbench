import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.browser.e2e.js",
  timeout: 15_000,
  retries: 0,
  use: { baseURL: "http://127.0.0.1:5174", screenshot: "only-on-failure" },
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]]
});
