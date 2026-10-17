import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  timeout: 45000,
  use: {
    baseURL: "http://127.0.0.1:5175",
    browserName: "chromium",
    viewport: { width: 1440, height: 900 },
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "../scripts/e2e-backend.sh",
      url: "http://127.0.0.1:9921/api/e2e-ready",
      timeout: 180000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: "npm run dev -- --port 5175",
      url: "http://127.0.0.1:5175",
      env: { API_TARGET: "http://127.0.0.1:9921" },
      timeout: 30000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});
