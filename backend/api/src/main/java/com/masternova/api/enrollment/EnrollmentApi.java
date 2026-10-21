package com.masternova.api.enrollment;

import java.util.UUID;

/**
 * enrollment's PUBLIC module API (CLAUDE.md §3), added in Phase 11 when engagement needed to know
 * who may review a course and speak in its Q&A without reading enrollment's table.
 */
public interface EnrollmentApi {

  /** Whether the learner is ACTIVELY enrolled in the course (a revoked enrollment is not). */
  boolean isEnrolled(UUID userId, UUID courseId);
}
