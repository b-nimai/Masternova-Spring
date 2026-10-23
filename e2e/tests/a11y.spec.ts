import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';
import { logIn, signUp, uniqueEmail } from './helpers';

/**
 * Phase UI.9: every main page scanned by axe-core (WCAG 2.x A + AA rules) in the real app. The
 * dark theme's contrast is the reason: a token change can make text unreadable without a test
 * failing anywhere else. Serious and critical findings fail the test; the rest are listed.
 */
async function expectAccessible(page: Page, url: string): Promise<void> {
  await page.goto(url);
  await page.waitForLoadState('networkidle');
  const { violations } = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const blocking = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  const summary = violations.map(
    (v) => `${v.impact} ${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`,
  );
  expect(blocking, `${url}\n${summary.join('\n')}`).toEqual([]);
}

test.describe('accessibility (axe)', () => {
  test('public pages', async ({ page, request }) => {
    test.setTimeout(90_000); // six pages, each loaded to network idle and scanned: not one test's 30 s
    const { items } = await (await request.get('/api/v1/courses?limit=1')).json();
    for (const url of ['/', '/courses', `/courses/${items[0].slug}`, '/search?q=python', '/login', '/signup']) {
      await expectAccessible(page, url);
    }
  });

  test("a learner's dashboard", async ({ page }) => {
    const email = uniqueEmail();
    await signUp(page, email);
    await page.goto('/login');
    await logIn(page, email);
    await expect(page.getByTestId('user-menu')).toBeVisible();
    for (const url of ['/account', '/learning', '/cart', '/account/orders', '/account/notifications']) {
      await expectAccessible(page, url);
    }
  });

  test("an instructor's courses and the course wizard", async ({ page }) => {
    await page.goto('/login');
    await logIn(page, 'instructor@seed.test', 'seed-password'); // the seeded instructor
    await expect(page.getByTestId('user-menu')).toBeVisible();
    await expectAccessible(page, '/instructor');
    const edit = await page.locator('a.title').first().getAttribute('href');
    await expectAccessible(page, edit!);
  });
});
