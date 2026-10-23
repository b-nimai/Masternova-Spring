import { expect, test } from '@playwright/test';

// UI.11: the effects are on when the OS allows motion (the rest of the suite runs with "reduce
// motion", playwright.config.ts). These prove the wiring, not the look.
test.use({ contextOptions: { reducedMotion: 'no-preference' } });

test('sections reveal as they scroll in, and nothing stays hidden', async ({ page }) => {
  await page.goto('/');
  const instructor = page.locator('section.instructor');
  await expect(instructor).toHaveClass(/mn-reveal/); // waiting, below the fold
  await expect(instructor).not.toHaveClass(/mn-revealed/);

  await instructor.scrollIntoViewIfNeeded();
  await expect(instructor).toHaveClass(/mn-revealed/);
  await expect(instructor).toHaveCSS('opacity', '1');
});

test('a course card glows where the pointer is', async ({ page }) => {
  await page.goto('/courses');
  const card = page.locator('app-course-card article').first();
  await expect(card).toHaveClass(/mn-pointer-glow/);
  const box = (await card.boundingBox())!;

  await page.mouse.move(box.x + 40, box.y + 30);
  await expect
    .poll(() => card.evaluate((el) => el.style.getPropertyValue('--mn-pointer-x')))
    .toBe('40px');
});

test('moving to another page runs a view transition; a filter does not', async ({ page }) => {
  await page.goto('/courses');
  await page.evaluate(() => {
    const started: string[] = [];
    (window as unknown as { started: string[] }).started = started;
    const original = document.startViewTransition.bind(document);
    document.startViewTransition = ((update: () => void) => {
      const transition = original(update);
      transition.ready.then(
        () => started.push('ran'),
        () => started.push('skipped'),
      );
      return transition;
    }) as typeof document.startViewTransition;
  });
  const started = () => page.evaluate(() => (window as unknown as { started: string[] }).started);

  await page.getByTestId('price-FREE').click(); // a filter: same page
  await expect.poll(started).toEqual(['skipped']);

  await page.locator('app-course-card a.title').first().click(); // a new page
  await expect.poll(started).toEqual(['skipped', 'ran']);
});
