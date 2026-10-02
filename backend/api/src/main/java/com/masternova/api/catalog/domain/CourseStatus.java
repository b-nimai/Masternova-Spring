package com.masternova.api.catalog.domain;

/**
 * Where a course is in its life. Phase 5 only READS it (visibility); Phase 6 replaces the
 * transitions with the State pattern (DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED).
 */
public enum CourseStatus {
  DRAFT,
  IN_REVIEW,
  PUBLISHED,
  ARCHIVED
}
