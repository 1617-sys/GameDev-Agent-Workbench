import { defineConfig } from "@playwright/test";

const externalBaseUrl = process.env.E2E_FRONTEND_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.browser.e2e.js",
  timeout: 30_000,
  retries: 0,
  use: {
    baseURL: externalBaseUrl || "http://127.0.0.1:4174",
    screenshot: "only-on-failure",
    trace: "off"
  },
  reporter: [["list"]],
  webServer: externalBaseUrl ? undefined : {
    command: "npm run dev -- --port 4174",
    url: "http://127.0.0.1:4174",
    reuseExistingServer: false,
    timeout: 30_000
  }
});
