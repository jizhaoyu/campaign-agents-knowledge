import { defineConfig, devices } from '@playwright/test';

const frontendPort = process.env.FRONTEND_PORT ?? '5173';
const baseURL = `http://127.0.0.1:${frontendPort}`;
const reuseExistingServer = process.env.PLAYWRIGHT_REUSE_SERVER !== 'false';

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: {
    timeout: 8_000
  },
  use: {
    baseURL,
    trace: 'on-first-retry'
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${frontendPort}`,
    url: baseURL,
    reuseExistingServer,
    timeout: 30_000
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
