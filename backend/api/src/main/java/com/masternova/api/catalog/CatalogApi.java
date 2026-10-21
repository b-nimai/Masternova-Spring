package com.masternova.api.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * catalog's PUBLIC module API (CLAUDE.md §3), added in Phase 8 when entitlement needed to decide
 * about a course without reading catalog's tables; commerce prices carts with it (Phase 9);
 * enrollment tracks lectures with it (Phase 10). Read-only — other modules ask, catalog owns —
 * except for {@link #adjustEnrollmentCount} and {@link #updateRating}, numbers catalog stores for
 * others so the lists can show and sort by them.
 */
public interface CatalogApi {

  /** The course's facts, in any state, or empty if there's no such course. */
  Optional<CourseFacts> courseFacts(UUID courseId);

  /**
   * Who teaches the course and its availability, in one small query, or empty if there's no such
   * course. Added in Phase 11 for engagement's access checks.
   */
  Optional<CourseStanding> courseStanding(UUID courseId);

  /** The same for the course this lecture belongs to (two indexed lookups). */
  Optional<CourseStanding> courseStandingOfLecture(UUID lectureId);

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

  /**
   * Sets the course's rating summary, joining the caller's transaction (engagement, Phase 11:
   * computed from its locked tally, ADR-0023). Doesn't bump the course's version.
   *
   * @param average 0–5, rounded to 2 places here; zero exactly when {@code count} is
   * @return whether the stored summary changed (false: it already said this, or no such course)
   */
  boolean updateRating(UUID courseId, int count, BigDecimal average);

  /**
   * {@link #updateRating} for many courses in ONE statement (the reconciler's pages of 500).
   *
   * @return how many stored summaries changed
   */
  int updateRatings(List<RatingSummary> ratings);

  /**
   * The courses whose stored rating isn't empty, in id order, after {@code afterCourseId} ({@code
   * null}: from the first) — the reconciler finds ratings no tally explains (Phase 11).
   */
  List<UUID> ratedCourses(UUID afterCourseId, int limit);
}
