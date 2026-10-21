package com.masternova.api.enrollment.domain;

import com.masternova.api.entitlement.EntitlementSource;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A learner's side of holding a course — one row per {@code (user, course)}. A value read from the
 * repository; every change is one SQL statement there (docs/lld/progress.md §3).
 *
 * @param source empty for a tombstone: a revoke that arrived before its grant (ADR-0021)
 * @param statusAt the time of the event that set {@code status}; older events are ignored
 * @param completedLectures rollup, refreshed by recount at each progress flush
 */
public record Enrollment(
    UUID userId,
    UUID courseId,
    Optional<EntitlementSource> source,
    EnrollmentStatus status,
    Instant enrolledAt,
    Instant statusAt,
    int completedLectures,
    Optional<UUID> lastLectureId,
    Optional<Instant> lastActivityAt) {

  public Enrollment {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(enrolledAt, "enrolledAt");
    Objects.requireNonNull(statusAt, "statusAt");
    Objects.requireNonNull(lastLectureId, "lastLectureId");
    Objects.requireNonNull(lastActivityAt, "lastActivityAt");
  }

  public boolean isActive() {
    return status == EnrollmentStatus.ACTIVE;
  }
}
