package com.masternova.api.entitlement.domain;

import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a policy may look at, fetched ONCE before any policy runs (LLD §3).
 *
 * <p>⭐ Policies receive data, never a repository: a rule that could query would turn a fixed-cost
 * decision into an N+1 that grows with the number of rules.
 *
 * @param lecture the lecture asked about, or empty for the course-wide question ("may I watch the
 *     course?") — then no lecture-level rule (preview) applies
 * @param entitlement the viewer's row for this course, whatever its status, or empty
 */
public record AccessContext(
    Viewer viewer,
    CourseFacts course,
    Optional<LectureFacts> lecture,
    Optional<Entitlement> entitlement,
    Instant now) {

  public AccessContext {
    Objects.requireNonNull(viewer, "viewer");
    Objects.requireNonNull(course, "course");
    Objects.requireNonNull(lecture, "lecture");
    Objects.requireNonNull(entitlement, "entitlement");
    Objects.requireNonNull(now, "now");
  }

  public boolean isOwner() {
    return viewer.userId().map(course.instructorId()::equals).orElse(false);
  }

  /**
   * ⭐ The scope of every DENY policy, written once: a denial that forgot it would lock the
   * instructor out of their own draft. Staff is never denied.
   */
  public boolean isStaff() {
    return viewer.admin() || isOwner();
  }

  /** The row's status now, or empty when the viewer has no row for this course. */
  public Optional<Entitlement.Status> entitlementStatus() {
    return entitlement.map(e -> e.status(now));
  }

  /** Whether the viewer holds a row in exactly this status now (no row: {@code false}). */
  public boolean entitlementIs(Entitlement.Status status) {
    return entitlementStatus().filter(status::equals).isPresent();
  }
}
