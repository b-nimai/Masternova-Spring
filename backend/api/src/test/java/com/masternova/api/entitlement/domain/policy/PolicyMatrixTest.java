package com.masternova.api.entitlement.domain.policy;

import static com.masternova.api.entitlement.domain.Contexts.PAID;
import static com.masternova.api.entitlement.domain.Contexts.PREVIEW;
import static com.masternova.api.entitlement.domain.Contexts.context;
import static com.masternova.api.entitlement.domain.Contexts.course;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.masternova.api.catalog.CourseFacts;
import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.catalog.CourseFacts.LectureFacts;
import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.AccessDecision.Allowed;
import com.masternova.api.entitlement.AccessDecision.Denied;
import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.domain.Contexts;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementPolicy;
import com.masternova.api.entitlement.domain.PolicyChain;
import com.masternova.api.entitlement.domain.Viewer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * ⭐ The whole decision table, written down as DATA: who × which course × which lecture × which row
 * → the answer. The seven REAL policies, sorted by their {@code @Order} exactly as Spring sorts
 * them for injection — but no Spring context, no database.
 *
 * <p>A new policy that changes any answer breaks a named row here, which is the point.
 */
class PolicyMatrixTest {

  /** The production chain: every policy, in Spring's order. */
  static List<EntitlementPolicy> policies() {
    List<EntitlementPolicy> all =
        new ArrayList<>(
            List.of(
                new ActiveEntitlementPolicy(),
                new FreeCoursePolicy(),
                new AdminPolicy(),
                new RevokedEntitlementPolicy(),
                new PreviewLecturePolicy(),
                new PublishedCoursePolicy(),
                new OwnerPolicy()));
    AnnotationAwareOrderComparator.sort(all); // what Spring does for a List<EntitlementPolicy>
    return all;
  }

  static final PolicyChain CHAIN = new PolicyChain(policies());

  static final Viewer ANONYMOUS = Viewer.anonymous();
  static final Viewer LEARNER = Contexts.learner();
  static final Viewer INSTRUCTOR = Contexts.instructor();
  static final Viewer ADMIN = Contexts.admin();

  static final CourseFacts PAID_COURSE = course(Availability.PUBLISHED, 49_900, true);
  static final CourseFacts FREE_COURSE = course(Availability.PUBLISHED, 0, true);
  static final CourseFacts UNPRICED = course(Availability.PUBLISHED, 0, false);
  static final CourseFacts DRAFT = course(Availability.UNPUBLISHED, 49_900, true);
  static final CourseFacts ARCHIVED = course(Availability.ARCHIVED, 49_900, true);

  static final Entitlement NONE = null;
  static final Entitlement ACTIVE = Contexts.active();
  static final Entitlement EXPIRED = Contexts.expired();
  static final Entitlement REVOKED = Contexts.revoked();

  static AccessDecision allowed(AccessGrant grant) {
    return new Allowed(grant);
  }

  static AccessDecision denied(DenialReason reason) {
    return new Denied(reason);
  }

  static Arguments row(
      String name,
      Viewer viewer,
      CourseFacts course,
      LectureFacts lecture,
      Entitlement entitlement,
      AccessDecision expected) {
    return argumentSet(name, viewer, course, lecture, entitlement, expected);
  }

  static Stream<Arguments> matrix() {
    return Stream.of(
        // ── a published, paid course ──────────────────────────────────────────────────────────
        row(
            "stranger, paid lecture",
            ANONYMOUS,
            PAID_COURSE,
            PAID,
            NONE,
            denied(DenialReason.NO_ENTITLEMENT)),
        row(
            "stranger, preview",
            ANONYMOUS,
            PAID_COURSE,
            PREVIEW,
            NONE,
            allowed(AccessGrant.PREVIEW_LECTURE)),
        row(
            "stranger, whole course",
            ANONYMOUS,
            PAID_COURSE,
            null,
            NONE,
            denied(DenialReason.NO_ENTITLEMENT)),
        row(
            "learner without a row",
            LEARNER,
            PAID_COURSE,
            PAID,
            NONE,
            denied(DenialReason.NO_ENTITLEMENT)),
        row(
            "buyer, paid lecture",
            LEARNER,
            PAID_COURSE,
            PAID,
            ACTIVE,
            allowed(AccessGrant.ENTITLED)),
        row(
            "buyer, preview (first allow named)",
            LEARNER,
            PAID_COURSE,
            PREVIEW,
            ACTIVE,
            allowed(AccessGrant.PREVIEW_LECTURE)),
        row(
            "buyer, whole course",
            LEARNER,
            PAID_COURSE,
            null,
            ACTIVE,
            allowed(AccessGrant.ENTITLED)),
        row(
            "expired rental → renew",
            LEARNER,
            PAID_COURSE,
            PAID,
            EXPIRED,
            denied(DenialReason.ENTITLEMENT_EXPIRED)),
        row(
            "expired rental, preview still open",
            LEARNER,
            PAID_COURSE,
            PREVIEW,
            EXPIRED,
            allowed(AccessGrant.PREVIEW_LECTURE)),
        row(
            "refunded",
            LEARNER,
            PAID_COURSE,
            PAID,
            REVOKED,
            denied(DenialReason.ENTITLEMENT_REVOKED)),
        row(
            "refunded beats a preview",
            LEARNER,
            PAID_COURSE,
            PREVIEW,
            REVOKED,
            denied(DenialReason.ENTITLEMENT_REVOKED)),
        row(
            "instructor, own course",
            INSTRUCTOR,
            PAID_COURSE,
            PAID,
            NONE,
            allowed(AccessGrant.OWNER)),
        row("admin, any course", ADMIN, PAID_COURSE, PAID, NONE, allowed(AccessGrant.ADMIN)),
        // ── free and unpriced ────────────────────────────────────────────────────────────────
        row(
            "stranger, free course",
            ANONYMOUS,
            FREE_COURSE,
            PAID,
            NONE,
            allowed(AccessGrant.FREE_COURSE)),
        row(
            "refunded beats a course made free",
            LEARNER,
            FREE_COURSE,
            PAID,
            REVOKED,
            denied(DenialReason.ENTITLEMENT_REVOKED)),
        row(
            "expired rental, course made free",
            LEARNER,
            FREE_COURSE,
            PAID,
            EXPIRED,
            allowed(AccessGrant.FREE_COURSE)),
        row(
            "0 but never priced is not free",
            ANONYMOUS,
            UNPRICED,
            PAID,
            NONE,
            denied(DenialReason.NO_ENTITLEMENT)),
        // ── not on sale ──────────────────────────────────────────────────────────────────────
        row(
            "stranger, draft preview",
            ANONYMOUS,
            DRAFT,
            PREVIEW,
            NONE,
            denied(DenialReason.COURSE_NOT_PUBLISHED)),
        row(
            "buyer keeps a course unpublished to edit",
            LEARNER,
            DRAFT,
            PAID,
            ACTIVE,
            allowed(AccessGrant.ENTITLED)),
        row(
            "refunded, draft",
            LEARNER,
            DRAFT,
            PAID,
            REVOKED,
            denied(DenialReason.ENTITLEMENT_REVOKED)),
        row("instructor, own draft", INSTRUCTOR, DRAFT, PAID, NONE, allowed(AccessGrant.OWNER)),
        row("admin, a draft", ADMIN, DRAFT, PAID, NONE, allowed(AccessGrant.ADMIN)),
        row(
            "buyer keeps an archived course",
            LEARNER,
            ARCHIVED,
            PAID,
            ACTIVE,
            allowed(AccessGrant.ENTITLED)),
        row(
            "stranger, archived preview",
            ANONYMOUS,
            ARCHIVED,
            PREVIEW,
            NONE,
            denied(DenialReason.COURSE_NOT_PUBLISHED)),
        row(
            "expired rental, archived",
            LEARNER,
            ARCHIVED,
            PAID,
            EXPIRED,
            denied(DenialReason.COURSE_NOT_PUBLISHED)),
        row(
            "refunded, archived (the personal reason first)",
            LEARNER,
            ARCHIVED,
            PAID,
            REVOKED,
            denied(DenialReason.ENTITLEMENT_REVOKED)),
        // ── staff are never denied by their own rows ─────────────────────────────────────────
        row("refunded admin", ADMIN, PAID_COURSE, PAID, REVOKED, allowed(AccessGrant.ADMIN)),
        row(
            "instructor with a revoked row",
            INSTRUCTOR,
            ARCHIVED,
            PAID,
            REVOKED,
            allowed(AccessGrant.OWNER)));
  }

  @ParameterizedTest
  @MethodSource("matrix")
  void decides(
      Viewer viewer,
      CourseFacts course,
      LectureFacts lecture,
      Entitlement entitlement,
      AccessDecision expected) {
    assertThat(CHAIN.evaluate(context(viewer, course, lecture, entitlement)).decision())
        .isEqualTo(expected);
  }

  /**
   * ⭐ Order changes only WHICH reason is named, never whether: every row, run through the chain
   * reversed, is still allowed or denied the same way.
   */
  @ParameterizedTest
  @MethodSource("matrix")
  void theVerdictDoesNotDependOnPolicyOrder(
      Viewer viewer,
      CourseFacts course,
      LectureFacts lecture,
      Entitlement entitlement,
      AccessDecision expected) {
    List<EntitlementPolicy> reversed = new ArrayList<>(policies());
    Collections.reverse(reversed);

    assertThat(
            new PolicyChain(reversed)
                .evaluate(context(viewer, course, lecture, entitlement))
                .decision()
                .isAllowed())
        .isEqualTo(expected.isAllowed());
  }

  @Test
  void springsOrderIsTheDocumentedOne() {
    assertThat(policies())
        .extracting(EntitlementPolicy::name)
        .containsExactly(
            "AdminPolicy",
            "OwnerPolicy",
            "RevokedEntitlementPolicy",
            "PublishedCoursePolicy",
            "FreeCoursePolicy",
            "PreviewLecturePolicy",
            "ActiveEntitlementPolicy"); // LLD §3's table
  }
}
