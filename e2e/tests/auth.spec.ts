import { expect, Page, test } from '@playwright/test';

const PASSWORD = 'correct-horse-battery';

/** A fresh account per test: tests never depend on each other or on leftover data. */
function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

async function signUp(page: Page, email: string, displayName = 'E2E Learner'): Promise<void> {
  await page.goto('/signup');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Display name').fill(displayName);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByLabel('Confirm password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign up' }).click();
  await expect(page.getByTestId('signup-done')).toContainText(email);
}

async function logIn(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}

test('sign up → log in → survive a reload → log out', async ({ page, context }) => {
  const email = uniqueEmail();
  await signUp(page, email);

  await page.getByRole('link', { name: 'Go to log in' }).click();
  await logIn(page, email);
  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByTestId('display-name')).toHaveText('E2E Learner');
  await expect(page.getByTestId('email-status')).toContainText('Not verified');

  // ⭐ The refresh token is a hardened cookie that page JavaScript can't read (ADR-0006) …
  const refresh = (await context.cookies()).find((c) => c.name === 'mn_refresh');
  expect(refresh).toMatchObject({ httpOnly: true, sameSite: 'Strict', path: '/api/v1/auth' });
  // … and the access token is never written to web storage (a JWT always starts with "eyJ")
  const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
  expect(storage).not.toContain('eyJ');

  // ⭐ A reload wipes the in-memory access token; the app initializer restores the session
  //    through the cookie, before the route guard runs, so the user stays on /account.
  await page.reload();
  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByTestId('display-name')).toHaveText('E2E Learner');

  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto('/account');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount$/); // authGuard sends us to log in
});

test('a wrong password gets one generic message', async ({ page }) => {
  const email = uniqueEmail();
  await signUp(page, email);
  await page.goto('/login');
  await logIn(page, email, 'not-the-password');
  await expect(page.getByTestId('form-error')).toHaveText('Email or password is incorrect.');
  await expect(page).toHaveURL(/\/login/);
});

test('a learner is kept out of the admin page', async ({ page }) => {
  const email = uniqueEmail();
  await signUp(page, email);
  await page.goto('/login');
  await logIn(page, email);
  await expect(page).toHaveURL(/\/account$/);

  await expect(page.getByRole('link', { name: 'Admin' })).toHaveCount(0); // not in the nav …
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/$/); // … and roleGuard redirects a typed URL too
});

test('the login returnUrl cannot send users to another site', async ({ page }) => {
  const email = uniqueEmail();
  await signUp(page, email);
  await page.goto('/login?returnUrl=//evil.example');
  await logIn(page, email);
  await expect(page).toHaveURL(/localhost:8081\/account$/);
});
