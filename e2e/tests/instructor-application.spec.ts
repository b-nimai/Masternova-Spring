import { expect, test } from '@playwright/test';
import { logIn, signUp, uniqueEmail } from './helpers';

// No one picks a role (UI.10): signup's Instructor tab files an application, an admin approves
// it, and the applicant's next token refresh carries INSTRUCTOR.
test('sign up as an instructor → an admin approves → the applicant can teach', async ({
  browser,
}) => {
  const email = uniqueEmail();
  const name = `Applicant ${email.slice(4, 17)}`; // unique in the admin's queue

  const applicant = await browser.newPage();
  await signUp(applicant, email, name, 'instructor');
  await expect(applicant.getByTestId('application-filed')).toContainText('waiting for an admin');
  await applicant.goto('/login');
  await logIn(applicant, email);
  await expect(applicant.getByTestId('user-menu')).toBeVisible();
  await applicant.goto('/account');
  await expect(applicant.getByTestId('teach-pending')).toContainText('waiting for an admin');

  const admin = await browser.newPage();
  await admin.goto('/login');
  await logIn(admin, 'admin@seed.test', 'seed-password');
  await expect(admin.getByTestId('user-menu')).toBeVisible();
  await admin.goto('/admin');
  const row = admin.getByTestId('application').filter({ hasText: name });
  await expect(admin.getByTestId('queue').or(admin.getByTestId('queue-empty'))).toBeVisible();
  // oldest first, keyset-paged: on a long-lived stack the new application may be pages down
  while ((await row.count()) === 0 && (await admin.getByTestId('queue-more').count()) > 0) {
    await admin.getByTestId('queue-more').click();
    await expect(admin.getByTestId('queue-more')).not.toHaveText('Loading…');
  }
  await row.getByTestId('approve').click();
  await expect(row).toHaveCount(0); // gone once the server agreed

  // the applicant's token still says learner: the account page refreshes it once
  await applicant.reload();
  await expect(applicant.getByTestId('teach-instructor')).toContainText("You're an instructor");
  await applicant.getByTestId('teach-courses').click();
  await expect(applicant).toHaveURL(/\/instructor$/);
});
