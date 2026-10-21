package com.masternova.api.engagement.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Who may speak about a course, and in which capacity — the result of the access check, never a
 * boolean. Anyone else is refused before one of these exists (403 {@code NOT_ENROLLED}).
 */
public sealed interface Participant {

  UUID userId();

  /** The course spoken about, and who teaches it (answers by them are flagged). */
  CourseRef course();

  /** Actively enrolled: may review the course and post in its Q&A. */
  record Learner(UUID userId, CourseRef course) implements Participant {}

  /** Teaches the course: answers its questions, never reviews it. */
  record Instructor(UUID userId, CourseRef course) implements Participant {}

  /** May read and post anywhere (moderation); reviews only courses they're enrolled in. */
  record Admin(UUID userId, CourseRef course) implements Participant {}

  record CourseRef(UUID courseId, UUID instructorId) {
    public CourseRef {
      Objects.requireNonNull(courseId, "courseId");
      Objects.requireNonNull(instructorId, "instructorId");
    }
  }
}
