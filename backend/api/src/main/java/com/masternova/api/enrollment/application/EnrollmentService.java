package com.masternova.api.enrollment.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseOffer;
import com.masternova.api.enrollment.domain.Enrollment;
import com.masternova.api.enrollment.domain.EnrollmentRepository;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.RuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is enrolled in what: follows entitlement's grants and revokes (through {@link
 * EnrollmentListener}) and enrolls learners in free courses. Catalog's {@code enrollment_count}
 * moves in the same transaction, and only when a row really changed — exact under retries.
 */
@Service
public class EnrollmentService {

  private final EnrollmentRepository enrollments;
  private final CatalogApi catalog;
  private final EntitlementApi entitlements;
  private final Clock clock;

  EnrollmentService(
      EnrollmentRepository enrollments,
      CatalogApi catalog,
      EntitlementApi entitlements,
      Clock clock) {
    this.enrollments = enrollments;
    this.catalog = catalog;
    this.entitlements = entitlements;
    this.clock = clock;
  }

  /** Access was granted at {@code at}: enroll, unless a newer revoke is already recorded. */
  @Transactional
  public boolean granted(UUID userId, UUID courseId, EntitlementSource source, Instant at) {
    boolean enrolled = enrollments.enroll(userId, courseId, source, at);
    if (enrolled) {
      catalog.adjustEnrollmentCount(courseId, +1);
    }
    return enrolled;
  }

  /** Access was revoked at {@code at}: leave the dashboard, keep the progress. */
  @Transactional
  public boolean revoked(UUID userId, UUID courseId, Instant at) {
    boolean ended = enrollments.revoke(userId, courseId, at);
    if (ended) {
      catalog.adjustEnrollmentCount(courseId, -1);
    }
    return ended;
  }

  /**
   * Repairs what no listener saw (see {@link EnrollmentRepository#enrollUnseenGrants}): one
   * transaction, the counter moved once per row that really changed.
   *
   * @return how many enrollments it changed — anything but 0 is worth a log line
   */
  @Transactional
  public int reconcile() {
    List<UUID> enrolled = enrollments.enrollUnseenGrants();
    List<UUID> ended = enrollments.revokeUnseenRevokes();
    enrolled.forEach(courseId -> catalog.adjustEnrollmentCount(courseId, +1));
    ended.forEach(courseId -> catalog.adjustEnrollmentCount(courseId, -1));
    return enrolled.size() + ended.size();
  }

  /**
   * Enrolls in a published, free course. ⭐ Grants {@code FREE_ENROLLMENT} AND enrolls in one
   * transaction, so the answer is already true when it returns; the listener's later copy of the
   * same grant is then a no-op. Idempotent: a second call (or a course already bought) changes
   * nothing.
   */
  @Transactional
  public Enrollment enrollFree(UUID userId, UUID courseId) {
    // ⭐ a grant after a revoke restores access, so re-enrolling would undo a refund or an admin.
    //    Asked FIRST: the enrollment may still be ACTIVE while the listener's revoke is on its way.
    if (entitlements.isRevoked(userId, courseId)) {
      throw new ForbiddenException(
          "ENTITLEMENT_REVOKED", "Your access to this course was withdrawn.");
    }
    Optional<Enrollment> held = enrollments.find(userId, courseId).filter(Enrollment::isActive);
    if (held.isPresent()) {
      return held.get(); // ⭐ already enrolled (bought, or a retry): the answer is the same
    }
    CourseOffer course =
        catalog.offers(List.of(courseId)).stream()
            .filter(o -> o.availability() == Availability.PUBLISHED) // drafts are invisible
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Course", courseId));
    if (!course.isFree()) {
      throw new RuleViolationException(
          "COURSE_NOT_FREE", "This course is for sale: buy it to enroll.");
    }
    entitlements.grant(userId, courseId, EntitlementSource.FREE_ENROLLMENT, Optional.empty());
    granted(userId, courseId, EntitlementSource.FREE_ENROLLMENT, clock.instant());
    return enrollments.find(userId, courseId).orElseThrow(); // ACTIVE: no newer revoke can exist
  }
}
