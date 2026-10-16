package com.masternova.api.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * catalog's PUBLIC module API (CLAUDE.md §3), added in Phase 8 when entitlement needed to decide
 * about a course without reading catalog's tables; commerce prices carts with it (Phase 9);
 * enrollment tracks lectures with it (Phase 10). Read-only — other modules ask, catalog owns —
 * except for {@link #adjustEnrollmentCount}, a counter catalog stores for others to sort by.
 */
public interface CatalogApi {

  /** The course's facts, in any state, or empty if there's no such course. */
  Optional<CourseFacts> courseFacts(UUID courseId);

  /** Whether there is such a course, in any state — without loading it. */
  boolean courseExists(UUID courseId);

  /** The facts of the course this lecture belongs to, or empty if there's no such lecture. */
  Optional<CourseFacts> courseFactsOfLecture(UUID lectureId);

  /**
   * The offers for these courses, in any state (the caller decides what is for sale), in one
   * statement. Unknown ids are simply absent. Added in Phase 9 for commerce's cart.
   */
  List<CourseOffer> offers(Collection<UUID> courseIds);

  /**
   * These courses with their lectures in curriculum order, in any state, in two statements. Unknown
   * ids are absent. Added in Phase 10 for the learner's dashboard.
   */
  List<CourseOutline> outlines(Collection<UUID> courseIds);

  /** These lectures with their course, in one statement; unknown ids are absent (Phase 10). */
  List<LectureOutline> lectures(Collection<UUID> lectureIds);

  /**
   * Moves the course's enrollment counter by {@code delta} (+1 enrolled, −1 left), joining the
   * caller's transaction. ⭐ The caller sends a delta only when its own row really changed, in the
   * same transaction, so the count is exact under retries.
   */
  void adjustEnrollmentCount(UUID courseId, int delta);
}
