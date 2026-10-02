import { expect, test } from '@playwright/test';
import { emailText, linkTo, logIn, signUp, uniqueEmail } from './helpers';

/**
 * ⭐ The whole notification feature through a real browser and a real inbox:
 * signup → (outbox → worker → SMTP) verification email → verify → welcome email → its
 * unsubscribe link → the preferences page agrees.
 */
test('verify by email, unsubscribe from the welcome email, manage preferences', async ({
  page,
  request,
}) => {
  const email = uniqueEmail();
  await signUp(page, email);

  // 1. the verification email arrives and its link verifies the account
  const verifyMail = await emailText(request, email, 'Confirm your email');
  await page.goto(linkTo(verifyMail, '/verify-email'));
  await expect(page.getByTestId('verified')).toBeVisible();

  // 2. verification publishes EmailVerified → the welcome email, with an unsubscribe link
  const welcomeMail = await emailText(request, email, 'Welcome to Masternova');
  const unsubscribeLink = linkTo(welcomeMail, '/unsubscribe');

  // 3. opening the link changes nothing until a person clicks (scanners open links too)
  await page.goto(unsubscribeLink);
  await expect(page.getByTestId('confirm-text')).toBeVisible();
  await page.getByRole('button', { name: 'Unsubscribe' }).click();
  await expect(page.getByTestId('done')).toContainText('News & tips');

  // 4. the settings page shows the opt-out; mandatory categories are locked on
  await page.getByRole('link', { name: 'Log in to manage email preferences' }).click();
  await logIn(page, email);
  await expect(page).toHaveURL(/\/account\/notifications$/);
  await expect(page.getByRole('switch', { name: 'News & tips' })).toHaveAttribute(
    'aria-checked',
    'false',
  );
  await expect(page.getByRole('switch', { name: 'Purchases' })).toBeDisabled();

  // 5. a toggle saves (optimistically) and survives a reload
  await page.getByRole('switch', { name: 'Reviews & questions' }).click();
  await expect(page.getByRole('switch', { name: 'Reviews & questions' })).toBeEnabled(); // saved
  await page.reload();
  await expect(page.getByRole('switch', { name: 'Reviews & questions' })).toHaveAttribute(
    'aria-checked',
    'false',
  );
});
