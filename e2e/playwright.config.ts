import { defineConfig, devices } from '@playwright/test';

/**
 * E2E smoke tests run a real browser against the real stack: nginx → Angular → Spring → Postgres.
 * They prove what unit tests can't: cookies, redirects and session restore in an actual browser.
 *
 * No retries: a flaky e2e test is a bug to fix, not to hide. The trace of a failed run is kept.
 */
export default defineConfig({
  testDir: './tests',
  forbidOnly: !!process.env['CI'], // a stray test.only must not silently skip the rest in CI
  retries: 0,
  workers: 1,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:8081',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
