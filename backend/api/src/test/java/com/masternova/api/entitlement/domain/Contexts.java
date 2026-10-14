package com.masternova.api.entitlement.domain;

import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.entitlement.EntitlementSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Test data for the policies: one course (a paid, published one by default) with a preview lecture
 * and a paid lecture, and helpers for each kind of viewer and row. Plain Java.
 */
public final class Contexts {

  public static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
  public static final UUID INSTRUCTOR = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  public static final UUID LEARNER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  public static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c0");
  public static final LectureFacts PREVIEW =
      new LectureFacts(UUID.fromString("00000000-0000-0000-0000-0000000000e1"), true, null);
  public static final LectureFacts PAID =
      new LectureFacts(UUID.fromString("00000000-0000-0000-0000-0000000000e2"), false, null);

  private Contexts() {}

  public static CourseFacts course(Availability availability, long priceMinor, boolean priceSet) {
    return new CourseFacts(
        COURSE, INSTRUCTOR, availability, priceMinor, priceSet, List.of(PREVIEW, PAID));
  }

  public static CourseFacts paidCourse() {
    return course(Availability.PUBLISHED, 49_900, true);
  }

  public static Viewer learner() {
    return Viewer.user(LEARNER, false);
  }

  public static Viewer instructor() {
    return Viewer.user(INSTRUCTOR, false);
  }

  public static Viewer admin() {
    return Viewer.user(UUID.fromString("00000000-0000-0000-0000-0000000000ad"), true);
  }

  public static Entitlement active() {
    return Entitlement.grant(
        LEARNER,
        COURSE,
        EntitlementSource.PURCHASE,
        NOW.minus(1, ChronoUnit.DAYS),
        Optional.empty());
  }

  public static Entitlement expired() {
    return Entitlement.grant(
        LEARNER,
        COURSE,
        EntitlementSource.PURCHASE,
        NOW.minus(30, ChronoUnit.DAYS),
        Optional.of(NOW.minus(1, ChronoUnit.HOURS)));
  }

  public static Entitlement revoked() {
    Entitlement e = active();
    return new Entitlement(
        e.userId(),
        e.courseId(),
        e.source(),
        e.grantedAt(),
        e.expiresAt(),
        Optional.of(new Entitlement.Revocation(NOW.minus(1, ChronoUnit.HOURS), "refund")));
  }

  public static AccessContext context(
      Viewer viewer, CourseFacts course, LectureFacts lecture, Entitlement row) {
    return new AccessContext(
        viewer, course, Optional.ofNullable(lecture), Optional.ofNullable(row), NOW);
  }
}
