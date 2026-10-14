package com.masternova.api.entitlement.domain;

import static com.masternova.api.entitlement.domain.Contexts.PAID;
import static com.masternova.api.entitlement.domain.Contexts.context;
import static com.masternova.api.entitlement.domain.Contexts.learner;
import static com.masternova.api.entitlement.domain.Contexts.paidCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.AccessDecision.Allowed;
import com.masternova.api.entitlement.AccessDecision.Denied;
import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.DenialReason;
import com.masternova.api.entitlement.domain.PolicyChain.PolicyVote;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The reduction itself, with stub policies — no Spring, no DB (LLD §3). */
class PolicyChainTest {

  private static final AccessContext CONTEXT = context(learner(), paidCourse(), PAID, null);

  private static EntitlementPolicy votes(String name, Vote vote) {
    return new EntitlementPolicy() {
      @Override
      public Vote decide(AccessContext context) {
        return vote;
      }

      @Override
      public String name() {
        return name;
      }
    };
  }

  private static final EntitlementPolicy PREVIEW =
      votes("preview", Vote.allow(AccessGrant.PREVIEW_LECTURE));
  private static final EntitlementPolicy REFUND =
      votes("refund", Vote.deny(DenialReason.ENTITLEMENT_REVOKED));

  private static AccessDecision decide(EntitlementPolicy... policies) {
    return new PolicyChain(List.of(policies)).evaluate(CONTEXT).decision();
  }

  @Test
  void anExplicitDenyWinsInEitherOrder() {
    // ⭐ the property that makes the chain safe to extend: order never changes WHETHER
    assertThat(decide(PREVIEW, REFUND)).isEqualTo(new Denied(DenialReason.ENTITLEMENT_REVOKED));
    assertThat(decide(REFUND, PREVIEW)).isEqualTo(new Denied(DenialReason.ENTITLEMENT_REVOKED));
  }

  @Test
  void orderOnlyPicksWhichReasonIsReported() {
    EntitlementPolicy owner = votes("owner", Vote.allow(AccessGrant.OWNER));
    assertThat(decide(owner, PREVIEW)).isEqualTo(new Allowed(AccessGrant.OWNER));
    assertThat(decide(PREVIEW, owner)).isEqualTo(new Allowed(AccessGrant.PREVIEW_LECTURE));

    EntitlementPolicy unpublished =
        votes("published", Vote.deny(DenialReason.COURSE_NOT_PUBLISHED));
    assertThat(decide(unpublished, REFUND))
        .isEqualTo(new Denied(DenialReason.COURSE_NOT_PUBLISHED));
  }

  @Test
  void closedByDefault() {
    EntitlementPolicy silent = votes("silent", Vote.ABSTAIN);
    assertThat(decide(silent, silent)).isEqualTo(new Denied(DenialReason.NO_ENTITLEMENT));
    assertThat(decide()).isEqualTo(new Denied(DenialReason.NO_ENTITLEMENT)); // no policies at all
  }

  @Test
  void anExpiredRowTurnsTheFallbackIntoRenew() {
    AccessContext expired = context(learner(), paidCourse(), PAID, Contexts.expired());
    assertThat(new PolicyChain(List.of()).evaluate(expired).decision())
        .isEqualTo(new Denied(DenialReason.ENTITLEMENT_EXPIRED));
  }

  @Test
  void aThrowingPolicyIsADenialNotAnError() {
    EntitlementPolicy broken =
        new EntitlementPolicy() {
          @Override
          public Vote decide(AccessContext context) {
            throw new IllegalStateException("bug");
          }

          @Override
          public String name() {
            return "broken";
          }
        };
    PolicyChain.Evaluation evaluation = new PolicyChain(List.of(PREVIEW, broken)).evaluate(CONTEXT);

    assertThat(evaluation.decision()).isEqualTo(new Denied(DenialReason.ACCESS_CHECK_FAILED));
    assertThat(evaluation.votes()).hasSize(2); // the others still voted, for the explanation
  }

  @Test
  void everyPolicyVotesAndTheVotesAreKeptInOrder() {
    assertThat(new PolicyChain(List.of(REFUND, PREVIEW)).evaluate(CONTEXT).votes())
        .containsExactly(
            new PolicyVote("refund", Vote.deny(DenialReason.ENTITLEMENT_REVOKED)),
            new PolicyVote("preview", Vote.allow(AccessGrant.PREVIEW_LECTURE)));
  }

  @Test
  void aNullVoteIsTreatedAsABrokenPolicy() {
    assertThat(decide(votes("null", null), PREVIEW))
        .isEqualTo(new Denied(DenialReason.ACCESS_CHECK_FAILED));
  }
}
