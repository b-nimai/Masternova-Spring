import { expect, test } from '@playwright/test';
import { cspViolations } from './helpers';

/**
 * The CSP (D5.5, frontend/security-headers.conf.template) against the real pages: every page loads
 * with the header and without a single violation. media.spec.ts checks the riskiest sources
 * (storage, blob: media, the hls.js worker) the same way.
 */
test.describe('content security policy', () => {
  test('the main pages load with a CSP and no violations', async ({ page }) => {
    const violations = cspViolations(page);

    const home = await page.goto('/');
    expect(home?.headers()['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(home?.headers()['x-content-type-options']).toBe('nosniff');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.locator('section.instructor').scrollIntoViewIfNeeded(); // the deferred sections load too

    await page.goto('/courses');
    await expect(page.getByTestId('status')).toContainText('courses');
    await page.locator('app-course-card').first().click();
    await expect(page).toHaveURL(/\/courses\/[^/?]+$/);

    await page.goto('/login');
    await expect(page.getByLabel('Email')).toBeVisible();

    expect(violations).toEqual([]);
  });
});
