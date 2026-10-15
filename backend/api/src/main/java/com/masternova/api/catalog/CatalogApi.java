package com.masternova.api.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * catalog's PUBLIC module API (CLAUDE.md §3), added in Phase 8 when entitlement needed to decide
 * about a course without reading catalog's tables; commerce prices carts with it (Phase 9).
 * Read-only: other modules ask, catalog owns.
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
}
