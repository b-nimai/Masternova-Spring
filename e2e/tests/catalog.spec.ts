import { expect, test } from "@playwright/test";

/**
 * The public catalog in a real browser, against the seeded catalog (docs/db/seed-catalog.sql; CI
 * and `make e2e` seed it). What only a browser can prove: the URL really is the state (reload and
 * Back keep the filters), the virtual scroll really loads the next page, and the deferred
 * curriculum really loads when scrolled into view.
 */
test.describe("catalog", () => {
  test("filters live in the URL: a reload and Back keep them", async ({
    page,
  }) => {
    await page.goto("/courses");
    await expect(page.getByTestId("status")).toContainText("courses");

    await page.getByTestId("price-FREE").click();
    await expect(page).toHaveURL(/price=FREE/);
    await expect(
      page.locator("app-course-card").getByTestId("price").first(),
    ).toHaveText("Free");

    await page.getByTestId("level-BEGINNER").click();
    await expect(page).toHaveURL(
      /price=FREE.*level=BEGINNER|level=BEGINNER.*price=FREE/,
    );

    await page.reload(); // ⭐ a bookmark or a shared link restores the same list
    await expect(
      page.getByRole("option", { name: "Beginner" }),
    ).toHaveAttribute("aria-selected", "true");
    for (const price of await page
      .locator("app-course-card")
      .getByTestId("price")
      .allTextContents()) {
      expect(price.trim()).toBe("Free");
    }

    await page.goBack(); // ⭐ Back undoes the last filter (level), not the whole page
    await expect(page).toHaveURL(/price=FREE/);
    await expect(page).not.toHaveURL(/level=/);
  });

  test("scrolling to the end of the list loads the next page with the cursor", async ({
    page,
  }) => {
    await page.goto("/courses");
    await expect(page.getByTestId("status")).toHaveText(/20 courses so far/);

    const nextPage = page.waitForRequest(
      (r) =>
        r.url().includes("/api/v1/courses?") && r.url().includes("cursor="),
    );
    await page
      .locator("cdk-virtual-scroll-viewport")
      .evaluate((el) => el.scrollTo(0, el.scrollHeight));
    await nextPage;

    await expect(page.getByTestId("status")).toHaveText(/40 courses/);
    // ⭐ virtual scroll: 40 courses loaded, only the visible handful exist in the DOM
    expect(await page.locator("app-course-card").count()).toBeLessThan(20);
  });

  test("a course page loads its curriculum when it scrolls into view", async ({
    page,
  }) => {
    await page.goto("/courses?sort=HIGHEST_RATED");
    const first = page.locator("app-course-card a.title").first();
    const title = (await first.textContent())?.trim() ?? "";
    await first.click();

    await expect(page).toHaveURL(/\/courses\/[a-z0-9-]+$/);
    await expect(page.getByTestId("title")).toHaveText(title);

    await page
      .getByRole("heading", { name: "Course content" })
      .scrollIntoViewIfNeeded();
    await expect(page.getByTestId("section-0")).toContainText(
      "Getting started",
    ); // @defer loaded
    await expect(page.getByTestId("preview").first()).toBeVisible();
  });

  test("an unknown course is a friendly 404", async ({ page }) => {
    await page.goto("/courses/no-such-course");
    await expect(page.getByTestId("not-found")).toContainText(
      "Course not found",
    );
  });
});
