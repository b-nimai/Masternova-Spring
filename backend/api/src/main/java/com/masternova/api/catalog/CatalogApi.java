package com.masternova.api.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * catalog's PUBLIC module API (CLAUDE.md §3), added in Phase 8 when entitlement needed to decide
 * about a course without reading catalog's tables. Read-only: other modules ask, catalog owns.
 */
public interface CatalogApi {

  /** The course's facts, in any state, or empty if there's no such course. */
  Optional<CourseFacts> courseFacts(UUID courseId);

  /** Whether there is such a course, in any state — without loading it. */
  boolean courseExists(UUID courseId);

  /** The facts of the course this lecture belongs to, or empty if there's no such lecture. */
  Optional<CourseFacts> courseFactsOfLecture(UUID lectureId);
}
