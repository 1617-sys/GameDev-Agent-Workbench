import { defineConfig } from "@playwright/test";

const mainWorkflowE2E = process.env.RUN_MAIN_WORKFLOW_E2E === "1";

export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.browser.e2e.js",
  timeout: mainWorkflowE2E ? 120_000 : 15_000,
  retries: 0,
  outputDir: process.env.PLAYWRIGHT_OUTPUT_DIR || "test-results",
  use: {
    baseURL: process.env.E2E_FRONTEND_BASE_URL || "http://127.0.0.1:5174",
    screenshot: "only-on-failure",
    // Raw Playwright traces can include Authorization request headers. The main E2E writes a redacted client trace instead.
    trace: "off"
  },
  reporter: [["list"], ["html", { open: "never", outputFolder: process.env.PLAYWRIGHT_HTML_REPORT_DIR || "playwright-report" }]]
});
