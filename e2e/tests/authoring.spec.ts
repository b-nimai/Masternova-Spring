import { expect, Page, test } from "@playwright/test";
import { logIn } from "./helpers";

/**
 * An instructor builds a course end to end in the real app (docs/lld/catalog-authoring.md),
 * as the SEEDED instructor (docs/db/seed-catalog.sql; CI and `make e2e` seed it).
 */
const INSTRUCTOR = "instructor@seed.test";
const PASSWORD = "seed-password";

async function signIn(page: Page): Promise<void> {
  await page.goto("/login");
  await logIn(page, INSTRUCTOR, PASSWORD);
  await expect(page).not.toHaveURL(/\/login/);
}

async function addLecture(
  page: Page,
  title: string,
  preview = false,
): Promise<void> {
  await page.getByTestId("new-lecture-0").fill(title);
  if (preview) {
    await page.getByRole("checkbox", { name: "Free preview" }).check();
  }
  await page.getByTestId("add-lecture-0").click();
  await expect(page.getByTestId(`lecture-${title}`)).toBeVisible();
}

test.describe("course authoring", () => {
  test("create → details (autosaved) → price → curriculum with undo/redo → submit", async ({
    page,
  }) => {
    const title = `E2E course ${Date.now()}`;
    await signIn(page);

    await page.goto("/instructor");
    await page.getByTestId("new-course").click();
    await page.getByTestId("title").fill(title);
    await page.getByTestId("category").click();
    await page.getByRole("option", { name: "CI/CD" }).click();
    await page.getByTestId("create").click();
    await expect(page).toHaveURL(/\/instructor\/courses\/[0-9a-f-]+\/edit$/);

    // Details: autosaved after a pause in typing
    await page
      .getByTestId("description")
      .fill(
        "Pipelines, caching, artifacts and deployments: a hands-on tour of CI/CD.",
      );
    await expect(page.getByTestId("save-state")).toHaveText(
      "All changes saved",
    );

    // Pricing: free is a decision too
    await page.getByRole("button", { name: "Next: pricing" }).click();
    await page.getByTestId("confirm-price").click();
    await expect(page.getByTestId("price-state")).toContainText(
      "Confirmed: Free",
    );

    // Curriculum: commands, then undo/redo from the server-side history
    await page.getByRole("button", { name: "Next: curriculum" }).click();
    await page.getByTestId("new-section").fill("Getting started");
    await page.getByTestId("add-section").click();
    await addLecture(page, "Welcome", true);
    await addLecture(page, "Setup");
    await addLecture(page, "Pipelines");

    await page.getByTestId("undo").click();
    await expect(page.getByTestId("lecture-Pipelines")).toHaveCount(0);
    await page.locator("body").click(); // focus out of the inputs: Ctrl+Z is ours, not the text field's
    await page.keyboard.press("Control+Shift+Z");
    await expect(page.getByTestId("lecture-Pipelines")).toBeVisible();
    await expect(page.getByTestId("totals")).toContainText("3 lectures");

    // Review: every requirement met → submit
    await page.getByRole("button", { name: "Next: review" }).click();
    await expect(page.getByTestId("req-NO_PREVIEW")).toHaveClass(/ok/);
    await page.getByTestId("submit").click();
    await expect(page.getByTestId("status")).toHaveText("IN_REVIEW");
  });

  /** ⭐ Two tabs on one course: the stale one is told, and nothing is overwritten. */
  test("a stale tab gets the conflict dialog instead of overwriting", async ({
    page,
    context,
  }) => {
    await signIn(page);
    await page.goto("/instructor/courses/new");
    await page.getByTestId("title").fill(`Two tabs ${Date.now()}`);
    await page.getByTestId("category").click();
    await page.getByRole("option", { name: "CI/CD" }).click();
    await page.getByTestId("create").click();
    await expect(page).toHaveURL(/\/edit$/);
    const url = page.url();

    const other = await context.newPage(); // same browser: the session is restored from the cookie
    await other.goto(url);
    await other.getByTestId("title").fill("Saved by the other tab");
    await expect(other.getByTestId("save-state")).toHaveText(
      "All changes saved",
    );

    await page.getByTestId("title").fill("Saved by the stale tab"); // still at the old version
    await expect(page.getByRole("dialog")).toContainText(
      "This course changed elsewhere",
    );
    await page.getByTestId("reload").click();
    await expect(page.getByTestId("title")).toHaveValue(
      "Saved by the other tab",
    );
  });
});
