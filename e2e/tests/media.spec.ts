import { expect, Page, test } from "@playwright/test";
import path from "node:path";
import { cspViolations, logIn } from "./helpers";

/**
 * Phase 7 end to end, in the real stack: the browser uploads a video straight to S3 (SeaweedFS),
 * the worker transcodes it with the real ffmpeg, the editor follows the SSE stream to "ready",
 * and the preview plays the HLS output through hls.js — docs/lld/media.md, video-pipeline.md.
 */
const INSTRUCTOR = "instructor@seed.test";
const PASSWORD = "seed-password";
const VIDEO = path.join(__dirname, "..", "fixtures", "lecture.mp4"); // 4 s, 320×180, H.264 + AAC

async function newCourseOnCurriculumStep(page: Page): Promise<void> {
  await page.goto("/login");
  await logIn(page, INSTRUCTOR, PASSWORD);
  await expect(page).not.toHaveURL(/\/login/);
  await page.goto("/instructor/courses/new");
  await page.getByTestId("title").fill(`Video course ${Date.now()}`);
  await page.getByTestId("category").click();
  await page.getByRole("option", { name: "CI/CD" }).click();
  await page.getByTestId("create").click();
  await expect(page).toHaveURL(/\/instructor\/courses\/[0-9a-f-]+\/edit$/);
  await page.getByRole("button", { name: "Next: pricing" }).click();
  await page.getByRole("button", { name: "Next: curriculum" }).click();
  await page.getByTestId("new-section").fill("Getting started");
  await page.getByTestId("add-section").click();
  await page.getByTestId("new-lecture-0").fill("Welcome"); // VIDEO is the default type
  await page.getByTestId("add-lecture-0").click();
  await expect(page.getByTestId("lecture-Welcome")).toBeVisible();
}

test.describe("media", () => {
  test("upload → transcode → live progress → ready → HLS preview plays", async ({
    page,
  }) => {
    test.setTimeout(180_000); // a real transcode in the worker
    const violations = cspViolations(page); // storage, blob: media and the hls.js worker (D5.5)
    await newCourseOnCurriculumStep(page);

    // the browser PUTs the parts straight to storage; the api only planned and completes
    const toStorage: string[] = [];
    page.on("request", (r) => {
      if (r.method() === "PUT" && r.url().includes("partNumber=")) toStorage.push(r.url());
    });
    await page.getByTestId("video-file-Welcome").setInputFiles(VIDEO);

    await expect(page.getByTestId("video-ready")).toBeVisible({ timeout: 150_000 });
    expect(toStorage.length).toBeGreaterThan(0);
    expect(toStorage.every((u) => !u.includes("/api/"))).toBe(true);

    await page.getByTestId("video-preview").click();
    const video = page.getByTestId("hls-video");
    await expect(video).toBeVisible();
    // hls.js parsed the ticketed master playlist and attached the stream: a duration means the
    // rung playlist and the init segment came back (from storage, presigned)
    await expect
      .poll(() => video.evaluate((v: HTMLVideoElement) => v.duration), { timeout: 30_000 })
      .toBeGreaterThan(3);
    await video.evaluate((v: HTMLVideoElement) => {
      v.muted = true;
      return v.play();
    });
    await expect
      .poll(() => video.evaluate((v: HTMLVideoElement) => v.currentTime), { timeout: 30_000 })
      .toBeGreaterThan(0.5); // ⭐ segments actually decoded and played
    expect(violations).toEqual([]);
  });
});
