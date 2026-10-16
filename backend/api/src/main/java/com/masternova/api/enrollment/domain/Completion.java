package com.masternova.api.enrollment.domain;

/**
 * When a lecture counts as done, and how much of a course is (docs/lld/progress.md §3). Static
 * rules: one caller each, no variation — not a Strategy.
 */
public final class Completion {

  private Completion() {}

  /**
   * Whether watching a lecture can complete it: a video with a known length. Articles and videos
   * not yet probed count for neither side of the percentage.
   */
  public static boolean isTrackable(boolean video, int durationSeconds) {
    return video && durationSeconds > 0;
  }

  /**
   * ⭐ 90 % of a KNOWN duration, in integers ({@code max × 10 ≥ duration × 9}: no floating point).
   * The credits at the end don't have to be watched. A lecture with no known length can't be
   * completed by watching.
   */
  public static boolean isComplete(int maxPositionSeconds, int durationSeconds) {
    return durationSeconds > 0 && maxPositionSeconds * 10L >= durationSeconds * 9L;
  }

  /** Completed / trackable, rounded down, 0–100 (the curriculum may have shrunk since). */
  public static int percent(int completed, int trackable) {
    return trackable == 0 ? 0 : Math.min(100, completed * 100 / trackable);
  }
}
