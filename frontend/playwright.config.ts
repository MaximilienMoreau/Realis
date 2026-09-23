import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e", fullyParallel: true, workers: 2, timeout: 60000,
  use: { baseURL: "http://127.0.0.1:3109", trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile", use: { ...devices["Pixel 7"] } }],
  webServer: { command: "npm run dev -- --hostname 127.0.0.1 --port 3109", url: "http://127.0.0.1:3109", reuseExistingServer: !process.env.CI },
});
