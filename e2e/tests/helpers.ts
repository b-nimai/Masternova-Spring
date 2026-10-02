import { APIRequestContext, expect, Page } from '@playwright/test';

export const PASSWORD = 'correct-horse-battery';

/** Mailpit's REST API (compose maps its web port to 8026). */
export const MAILPIT_URL = process.env['MAILPIT_URL'] ?? 'http://localhost:8026';

/** A fresh account per test: tests never depend on each other or on leftover data. */
export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

export async function signUp(page: Page, email: string, displayName = 'E2E Learner'): Promise<void> {
  await page.goto('/signup');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Display name').fill(displayName);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByLabel('Confirm password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign up' }).click();
  await expect(page.getByTestId('signup-done')).toContainText(email);
}

export async function logIn(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
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
