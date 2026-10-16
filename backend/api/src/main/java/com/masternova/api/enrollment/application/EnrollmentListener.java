package com.masternova.api.enrollment.application;

import com.masternova.api.entitlement.EntitlementGranted;
import com.masternova.api.entitlement.EntitlementRevoked;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps enrollments in step with entitlement — whatever granted it (a purchase, a free enrollment,
 * an admin) and whatever revoked it (a refund, an admin).
 *
 * <p>⭐ OBSERVER, durable. {@code @ApplicationModuleListener} = after the publisher's commit, on
 * another thread, in a new transaction; Modulith's registry (ADR-0021) wrote a publication row in
 * the PUBLISHING transaction and deletes it only when this method returns. A crash, a deploy or an
 * exception here leaves the row, and the resubmitter runs it again. So entitlement never learns
 * enrollment exists, enrollment can't fail a payment, and it can be late but never lost.
 *
 * <p>Delivery is at-least-once and unordered: each event carries its own time, and the repository
 * applies only events newer than the row's status.
 */
@Component
@DesignPattern(
    value = Pattern.OBSERVER,
    role = "Observer: durable module listener of entitlement's events",
    note = "patterns/docs/07-observer.md")
class EnrollmentListener {

  private final EnrollmentService enrollments;

  EnrollmentListener(EnrollmentService enrollments) {
    this.enrollments = enrollments;
  }

  @ApplicationModuleListener
  void on(EntitlementGranted event) {
    enrollments.granted(
        UUID.fromString(event.userId()),
        UUID.fromString(event.courseId()),
        event.source(),
        event.grantedAt());
  }

  @ApplicationModuleListener
  void on(EntitlementRevoked event) {
    enrollments.revoked(
        UUID.fromString(event.userId()), UUID.fromString(event.courseId()), event.revokedAt());
  }
}
