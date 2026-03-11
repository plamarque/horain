import { defineConfig, devices } from '@playwright/test'
import { API_BASE, API_KEY } from './e2e/e2eEnv'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:4173',
    ignoreHTTPSErrors: true,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run build && npx serve -s dist -l 4173',
    url: 'http://localhost:4173',
    reuseExistingServer: true,
    timeout: 120000,
    env: {
      VITE_API_URL: API_BASE,
      VITE_API_KEY: API_KEY,
    },
  },
})
