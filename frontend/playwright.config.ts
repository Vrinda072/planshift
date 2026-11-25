import { defineConfig, devices } from "@playwright/test";

/**
 * Full-stack browser tests. They expect the frontend, API and database to be running already
 * (`docker compose up -d --build`). Locally they use the installed Chrome; CI installs Playwright's Chromium.
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.e2e.ts",
  timeout: 90_000,
  use: {
    ...devices["Desktop Chrome"],
    channel: process.env.CI ? undefined : "chrome",
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    trace: "retain-on-failure",
  },
});
