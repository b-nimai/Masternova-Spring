import { APIRequestContext, expect, Page } from '@playwright/test';

export const PASSWORD = 'correct-horse-battery';

/** Mailpit's REST API (compose maps its web port to 8026). */
export const MAILPIT_URL = process.env['MAILPIT_URL'] ?? 'http://localhost:8026';

/** A fresh account per test: tests never depend on each other or on leftover data. */
export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

export async function signUp(
  page: Page,
  email: string,
  displayName = 'E2E Learner',
  as: 'student' | 'instructor' = 'student',
): Promise<void> {
  await page.goto('/signup');
  if (as === 'instructor') {
    await page.getByRole('radio', { name: 'Instructor' }).click(); // mat-button-toggle: a radio
  }
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Display name').fill(displayName);
  await page.getByLabel('Create password').fill(PASSWORD);
  await page.getByLabel('Confirm password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByTestId('signup-done')).toContainText(email);
}

export async function logIn(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(password); // not the eye button
  await page.getByRole('button', { name: 'Log in' }).click();
}

/**
 * Waits until the worker has delivered an email with this subject to this address, then returns
 * its plaintext body. Polls: delivery is asynchronous (outbox → worker → SMTP), by design.
 */
export async function emailText(
  request: APIRequestContext,
  to: string,
  subjectContains: string,
): Promise<string> {
  let id = '';
  await expect
    .poll(
      async () => {
        const res = await request.get(`${MAILPIT_URL}/api/v1/search`, {
          params: { query: `to:"${to}"` },
        });
        const { messages } = (await res.json()) as {
          messages: { ID: string; Subject: string }[];
        };
        id = messages.find((m) => m.Subject.includes(subjectContains))?.ID ?? '';
        return id;
      },
      { message: `an email "${subjectContains}" to ${to}`, timeout: 15_000 },
    )
    .not.toBe('');
  const message = await request.get(`${MAILPIT_URL}/api/v1/message/${id}`);
  return ((await message.json()) as { Text: string }).Text;
}

/** The first URL in an email body that contains `path`. */
export function linkTo(text: string, path: string): string {
  const match = text.match(new RegExp(`https?://\\S*${path}\\S*`));
  if (!match) {
    throw new Error(`no ${path} link in:\n${text}`);
  }
  return match[0];
}

/**
 * Collects every Content-Security-Policy violation the page reports (D5.5). A blocked font, image
 * or worker breaks nothing a test asserts on, so without this a CSP regression passes silently.
 * Call before navigating; assert `toEqual([])` at the end.
 */
export function cspViolations(page: Page): string[] {
  const seen: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error' && /Content Security Policy/i.test(msg.text())) {
      seen.push(msg.text());
    }
  });
  return seen;
}
