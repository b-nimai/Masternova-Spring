package com.masternova.api.entitlement.domain.policy;

import static com.masternova.api.entitlement.domain.Contexts.PAID;
import static com.masternova.api.entitlement.domain.Contexts.PREVIEW;
import static com.masternova.api.entitlement.domain.Contexts.context;
import static com.masternova.api.entitlement.domain.Contexts.course;
import static com.masternova.api.entitlement.domain.Contexts.instructor;
import static com.masternova.api.entitlement.domain.Contexts.learner;
import static com.masternova.api.entitlement.domain.Contexts.paidCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.catalog.CourseFacts.Availability;
import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.domain.Contexts;
import com.masternova.api.entitlement.domain.Vote;
import org.junit.jupiter.api.Test;

/** Each rule on its own: what it votes, and — for the DENY rules — that it scopes itself. */
class PoliciesTest {

  @Test
  void theUnpublishedRuleNeverDeniesStaff() {
    var draft = course(Availability.UNPUBLISHED, 49_900, true);
    var policy = new PublishedCoursePolicy();

    assertThat(policy.decide(context(learner(), draft, PAID, null)))
        .isEqualTo(Vote.deny(DenialReason.COURSE_NOT_PUBLISHED));
    assertThat(policy.decide(context(instructor(), draft, PAID, null))).isEqualTo(Vote.ABSTAIN);
    assertThat(policy.decide(context(Contexts.admin(), draft, PAID, null))).isEqualTo(Vote.ABSTAIN);
  }

  @Test
  void anArchivedCourseStaysOpenToThoseWhoPaid() {
    var archived = course(Availability.ARCHIVED, 49_900, true);
    var policy = new PublishedCoursePolicy();

    assertThat(policy.decide(context(learner(), archived, PAID, Contexts.active())))
        .isEqualTo(Vote.ABSTAIN);
    assertThat(policy.decide(context(learner(), archived, PAID, null)))
        .isEqualTo(Vote.deny(DenialReason.COURSE_NOT_PUBLISHED));
    assertThat(policy.decide(context(learner(), archived, PAID, Contexts.expired())))
        .isEqualTo(Vote.deny(DenialReason.COURSE_NOT_PUBLISHED));
  }

  @Test
  void aBuyerKeepsACourseItsInstructorUnpublishedToEdit() {
    var draft = course(Availability.UNPUBLISHED, 49_900, true);
    assertThat(
            new PublishedCoursePolicy().decide(context(learner(), draft, PAID, Contexts.active())))
        .isEqualTo(Vote.ABSTAIN);
  }

  @Test
  void theRevokedRuleNeverDeniesStaff() {
    var policy = new RevokedEntitlementPolicy();
    assertThat(policy.decide(context(learner(), paidCourse(), PAID, Contexts.revoked())))
        .isEqualTo(Vote.deny(DenialReason.ENTITLEMENT_REVOKED));
    assertThat(policy.decide(context(Contexts.admin(), paidCourse(), PAID, Contexts.revoked())))
        .isEqualTo(Vote.ABSTAIN);
  }

  @Test
  void zeroIsFreeOnlyOnceItWasPriced() {
    var policy = new FreeCoursePolicy();
    assertThat(
            policy.decide(context(learner(), course(Availability.PUBLISHED, 0, true), PAID, null)))
        .isEqualTo(Vote.allow(AccessGrant.FREE_COURSE));
    assertThat(
            policy.decide(context(learner(), course(Availability.PUBLISHED, 0, false), PAID, null)))
        .isEqualTo(Vote.ABSTAIN);
  }

  @Test
  void aPreviewOpensOneLectureNotTheCourse() {
    var policy = new PreviewLecturePolicy();
    assertThat(policy.decide(context(learner(), paidCourse(), PREVIEW, null)))
        .isEqualTo(Vote.allow(AccessGrant.PREVIEW_LECTURE));
    assertThat(policy.decide(context(learner(), paidCourse(), PAID, null))).isEqualTo(Vote.ABSTAIN);
    assertThat(policy.decide(context(learner(), paidCourse(), null, null))).isEqualTo(Vote.ABSTAIN);
  }

  @Test
  void onlyAnActiveRowAllows() {
    var policy = new ActiveEntitlementPolicy();
    assertThat(policy.decide(context(learner(), paidCourse(), PAID, Contexts.active())))
        .isEqualTo(Vote.allow(AccessGrant.ENTITLED));
    assertThat(policy.decide(context(learner(), paidCourse(), PAID, Contexts.expired())))
        .isEqualTo(Vote.ABSTAIN);
    assertThat(policy.decide(context(learner(), paidCourse(), PAID, Contexts.revoked())))
        .isEqualTo(Vote.ABSTAIN);
  }
}
