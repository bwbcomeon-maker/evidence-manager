import { defineConfig, devices } from '@playwright/test'

/**
 * 权限自动化 E2E：需先启动前端(npm run dev)与后端(默认 8081)，或使用 webServer 自动启动前端。
 * 失败截图与报告输出到 test-results/
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    ['html', { outputFolder: 'test-results/html', open: 'never' }],
    ['list']
  ],
  outputDir: 'test-results/test-output',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry'
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } }
  ],
  webServer: process.env.CI
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:3000',
        reuseExistingServer: !!process.env.PLAYWRIGHT_BASE_URL,
        timeout: 60_000
      }
})
