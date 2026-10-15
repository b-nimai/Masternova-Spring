import { expect, test } from '@playwright/test';
import { emailText, logIn, signUp, uniqueEmail } from './helpers';

/**
 * ⭐ A purchase through a real browser (Phase 9.9), with the stack's FAKE payment provider (no
 * Razorpay keys in CI): course page → Buy now → cart priced by the server → checkout → the
 * simulated payment modal → confirm → the result page → the course opens → the receipt email
 * arrives (OrderPaid → outbox → worker → SMTP).
 */
test('buy a course: cart → checkout → paid → the course opens and the receipt arrives', async ({
  page,
  request,
}) => {
  const email = uniqueEmail();
  await signUp(page, email);
  await page.goto('/login');
  await logIn(page, email);
  await expect(page.getByTestId('user-menu')).toBeVisible();

  // 1. a paid course from the seeded catalog
  await page.goto('/courses?price=PAID');
  await page.locator('app-course-card a.title').first().click();
  const title = (await page.getByTestId('title').textContent())?.trim() ?? '';
  await expect(page.getByTestId('cta-DENIED')).toBeVisible();

  // 2. Buy now → the cart, priced by the server
  await page.getByTestId('buy-now').click();
  await expect(page).toHaveURL(/\/cart$/);
  await expect(page.getByTestId('cart-item')).toContainText(title);
  await expect(page.getByTestId('total')).not.toBeEmpty();
  await expect(page.getByTestId('cart-link')).toContainText('1'); // the toolbar badge

  // 3. checkout → the FAKE provider's modal stands in for Razorpay's
  await page.getByTestId('checkout').click();
  await page.getByTestId('fake-pay').click();

  // 4. confirmed: the result page, and the course is ours
  await expect(page).toHaveURL(/\/orders\/[0-9a-f-]+$/);
  await expect(page.getByTestId('result-paid')).toBeVisible();
  await page.getByTestId('start-learning').first().click();
  await expect(page.getByTestId('cta-ALLOWED')).toContainText('Start learning');

  // 5. the cart was emptied of what was bought, and the order is in the history
  await page.goto('/account/orders');
  await expect(page.locator('tr[mat-row]').first()).toContainText(title);
  await expect(page.locator('tr[mat-row]').first()).toContainText('Paid');

  // 6. the receipt, emailed by the worker
  const receipt = await emailText(request, email, 'Your receipt');
  expect(receipt).toContain(title);
  expect(receipt).toContain('Total paid');
});
