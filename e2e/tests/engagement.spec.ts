import { expect, test } from "@playwright/test";
import { logIn, signUp, uniqueEmail } from "./helpers";

/**
 * Phase 11 in a real browser: the typeahead and the ranked results page (public), and a learner
 * reviewing a free course they enrolled in. Against the seeded catalog, whose titles name real
 * subjects ("Kubernetes …", "Docker …"). The stack may run without Ollama: search must then
 * still answer, from keywords.
 */
test.describe("engagement + search", () => {
  test("the typeahead suggests while typing, and Enter shows ranked results", async ({
    page,
  }) => {
    await page.goto("/courses");
    await page.getByTestId("search-toggle").click(); // the nav bar's search opens on demand
    const box = page.getByTestId("search-input");
    await box.fill("kuberne"); // a prefix: trigram word similarity

    const suggestion = page.getByTestId("suggestion").first();
    await expect(suggestion).toContainText("Kubernetes");

    // Enter with no option highlighted submits the text as typed (no Escape: on a type="search"
    // input, Chromium's Escape CLEARS the text). A whole word: a prefix is the typeahead's job, and
    // full-text search stems "kuberne" apart from "kubernetes" (CI has no model to match by meaning)
    await box.fill("kubernetes");
    await box.press("Enter");
    await expect(page).toHaveURL(/\/search\?q=kubernetes/);
    await expect(page.getByTestId("search-title")).toContainText("kubernetes");
    await expect(page.getByTestId("results").locator("app-course-card").first()).toBeVisible();
  });

  test("an enrolled learner reviews a free course and sees it counted", async ({
    page,
  }) => {
    const email = uniqueEmail();
    const name = `Reviewer ${email.slice(4, 17)}`; // unique: earlier runs reviewed the same course
    await signUp(page, email, name);
    await page.goto("/login");
    await logIn(page, email);
    await expect(page.getByTestId("user-menu")).toBeVisible(); // logged in before navigating away

    await page.goto("/courses?price=FREE");
    await page.locator("app-course-card a.title").first().click();
    await page.getByTestId("enroll").click();
    await expect(page.getByTestId("enroll")).toHaveCount(0); // enrolled: the CTA moved on

    const reviews = page.getByTestId("reviews");
    await reviews.scrollIntoViewIfNeeded(); // @defer (on viewport)
    await page.getByTestId("star-4").click();
    await page.getByTestId("review-body").fill("Clear and practical.");
    await page.getByTestId("save-review").click();

    await expect(
      reviews.getByTestId("review-item").filter({ hasText: name }),
    ).toContainText("Clear and practical.");
    await expect(page.getByTestId("save-review")).toHaveText(/Update review/);
  });
});
