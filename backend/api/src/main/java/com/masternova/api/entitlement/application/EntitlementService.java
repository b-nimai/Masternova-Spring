package com.masternova.api.entitlement.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.entitlement.EntitlementGranted;
import com.masternova.api.entitlement.EntitlementRevoked;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.application.CourseAccess.LectureAccess;
import com.masternova.api.entitlement.domain.AccessContext;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementRepository;
import com.masternova.api.entitlement.domain.PolicyChain;
import com.masternova.api.entitlement.domain.PolicyChain.Evaluation;
import com.masternova.api.entitlement.domain.Viewer;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.IdentityApi;
import com.masternova.api.identity.Role;
import com.masternova.api.platform.EventPublisher;
import com.masternova.api.platform.NotFoundException;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The entitlement module's single entry point: controllers, the {@code @entitlements} SpEL bean and
 * (Phase 9) commerce all come through here.
 *
 * <p>⭐ FACADE. Force: every caller needs the same sequence — load catalog's facts and the viewer's
 * row ONCE, build the context, run the chain — and must not learn the parts (the cache, the chain,
 * catalog's API). The facade holds the SEQUENCE and the transaction boundary; the RULES live in the
 * policies, so it stays thin.
 *
 * <p>Decisions run without a transaction: catalog's read has its own, and no connection is held
 * across the Redis lookup.
 */
@Service
@DesignPattern(
    value = Pattern.FACADE,
    role = "Facade: one entry point over facts, cache, chain and transactions",
    note = "patterns/docs/14-facade.md")
public class EntitlementService implements EntitlementApi {

  private final CatalogApi catalog;
  private final IdentityApi identity;
  private final EntitlementRepository entitlements;
  private final PolicyChain chain;
  private final EventPublisher events;
  private final Clock clock;

  EntitlementService(
      CatalogApi catalog,
      IdentityApi identity,
      EntitlementRepository entitlements,
      PolicyChain chain,
      EventPublisher events,
      Clock clock) {
    this.catalog = catalog;
    this.identity = identity;
    this.entitlements = entitlements;
    this.chain = chain;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public AccessDecision decide(Optional<CurrentUser> viewer, UUID lectureId) {
    return evaluate(viewerOf(viewer), lectureId).decision();
  }

  @Override
  public Set<UUID> heldCourses(UUID userId, Collection<UUID> courseIds) {
    Instant now = clock.instant();
    return courseIds.stream() // ⭐ one cached lookup each: a cart is ≤ 20 courses
        .distinct()
        .filter(
            courseId ->
                entitlements
                    .find(userId, courseId)
                    .filter(e -> e.status(now) == Entitlement.Status.ACTIVE)
                    .isPresent())
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean isRevoked(UUID userId, UUID courseId) {
    return entitlements
        .find(userId, courseId)
        .filter(e -> e.status(clock.instant()) == Entitlement.Status.REVOKED)
        .isPresent();
  }

  /** The course-wide decision and one per lecture — the row is read once for all of them. */
  public CourseAccess courseAccess(Optional<CurrentUser> viewer, UUID courseId) {
    CourseFacts course =
        catalog.courseFacts(courseId).orElseThrow(() -> new NotFoundException("Course", courseId));
    Viewer who = viewerOf(viewer);
    Optional<Entitlement> row = rowOf(who, course);
    Instant now = clock.instant();
    return new CourseAccess(
        courseId,
        evaluate(who, course, Optional.empty(), row, now).decision(),
        course.lectures().stream()
            .map(
                l ->
                    new LectureAccess(
                        l.lectureId(), evaluate(who, course, Optional.of(l), row, now).decision()))
            .toList());
  }

  /**
   * Every policy's vote for this user as a LEARNER (admins can always watch; the question an admin
   * asks is "why can't this learner?"). For support, behind the admin role.
   */
  public Evaluation explain(UUID userId, UUID lectureId) {
    return evaluate(Viewer.user(userId, false), lectureId);
  }

  @Override
  @Transactional
  public boolean grant(
      UUID userId, UUID courseId, EntitlementSource source, Optional<Instant> expiresAt) {
    requireUserAndCourse(userId, courseId);
    Instant now = clock.instant();
    boolean changed =
        entitlements.grant(Entitlement.grant(userId, courseId, source, now, expiresAt));
    if (changed) { // ⭐ only a real change is news: 50 copies of one grant → one event
      events.publish(
          new EntitlementGranted(
              userId.toString(), courseId.toString(), source, expiresAt.orElse(null), now));
    }
    return changed;
  }

  @Override
  @Transactional
  public boolean revoke(UUID userId, UUID courseId, String reason) {
    requireUserAndCourse(userId, courseId);
    Instant now = clock.instant();
    boolean changed =
        entitlements.revoke(userId, courseId, new Entitlement.Revocation(now, reason));
    if (changed) {
      events.publish(new EntitlementRevoked(userId.toString(), courseId.toString(), reason, now));
    }
    return changed;
  }

  private Evaluation evaluate(Viewer viewer, UUID lectureId) {
    CourseFacts course =
        catalog
            .courseFactsOfLecture(lectureId)
            .orElseThrow(() -> new NotFoundException("Lecture", lectureId));
    return evaluate(
        viewer, course, course.lecture(lectureId), rowOf(viewer, course), clock.instant());
  }

  private Evaluation evaluate(
      Viewer viewer,
      CourseFacts course,
      Optional<LectureFacts> lecture,
      Optional<Entitlement> row,
      Instant now) {
    return chain.evaluate(new AccessContext(viewer, course, lecture, row, now));
  }

  /** Anonymous viewers have no row: no lookup at all. */
  private Optional<Entitlement> rowOf(Viewer viewer, CourseFacts course) {
    return viewer.userId().flatMap(id -> entitlements.find(id, course.courseId()));
  }

  private void requireUserAndCourse(UUID userId, UUID courseId) {
    if (identity.displayName(userId).isEmpty()) {
      throw new NotFoundException("User", userId);
    }
    if (!catalog.courseExists(courseId)) {
      throw new NotFoundException("Course", courseId);
    }
  }

  private static Viewer viewerOf(Optional<CurrentUser> user) {
    return user.map(u -> Viewer.user(u.id(), u.has(Role.ADMIN))).orElseGet(Viewer::anonymous);
  }
}
