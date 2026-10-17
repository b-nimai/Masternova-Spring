package com.masternova.api.enrollment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where "Continue" takes a learner (docs/lld/progress.md §3). Static rules, like {@link
 * Completion}: one caller, no variation.
 */
public final class Resume {

  /** Resuming this close to the end would show the credits: start the lecture over instead. */
  static final int END_MARGIN_SECONDS = 10;

  private Resume() {}

  /**
   * The lecture to continue: the last one watched, unless it is complete, then the next trackable
   * one in curriculum order; nothing watched (or the last one is gone) → the first. Past the end of
   * a course that isn't complete (they skipped ahead) → back to the first. Empty only when the
   * course has nothing to watch, or every lecture is complete.
   *
   * @param trackable the course's trackable lectures, in curriculum order
   * @param courseComplete every trackable lecture is complete
   */
  public static Optional<UUID> lecture(
      List<UUID> trackable, Optional<UUID> last, boolean lastComplete, boolean courseComplete) {
    if (courseComplete) {
      return Optional.empty();
    }
    int at = last.map(trackable::indexOf).orElse(-1);
    if (at < 0) {
      return trackable.stream().findFirst();
    }
    int next = lastComplete ? at + 1 : at;
    return Optional.of(trackable.get(next < trackable.size() ? next : 0));
  }

  /** The second to start a lecture at: where they stopped, or 0 if that was the very end. */
  public static int position(int lastPositionSeconds, int durationSeconds) {
    return durationSeconds > 0 && lastPositionSeconds >= durationSeconds - END_MARGIN_SECONDS
        ? 0
        : lastPositionSeconds;
  }
}
