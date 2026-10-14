package com.masternova.patterns.chain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChainTest {

  /** A refunded learner opening a preview lecture: one rule allows, one denies. */
  private static final Access REFUNDED_ON_PREVIEW = new Access(false, false, true, false, true);

  @Test
  void firstMatchLetsALaterAllowDefeatARefund() {
    // the same two rules; only the order differs — and so does the answer ❌
    assertThat(new FirstMatchChain(List.of(Rule.REFUNDED, Rule.PREVIEW)).decide(REFUNDED_ON_PREVIEW))
        .isEqualTo(new Vote.Deny("refunded"));
    assertThat(new FirstMatchChain(List.of(Rule.PREVIEW, Rule.REFUNDED)).decide(REFUNDED_ON_PREVIEW))
        .isEqualTo(new Vote.Allow("preview"));
  }

  @Test
  void denyWinsIsTheSameInEveryOrder() {
    assertThat(new DenyWinsChain(List.of(Rule.REFUNDED, Rule.PREVIEW)).decide(REFUNDED_ON_PREVIEW))
        .isEqualTo(new Vote.Deny("refunded"));
    assertThat(new DenyWinsChain(List.of(Rule.PREVIEW, Rule.REFUNDED)).decide(REFUNDED_ON_PREVIEW))
        .isEqualTo(new Vote.Deny("refunded"));
  }

  @Test
  void theDenyScopesItselfSoStaffIsNeverLockedOut() {
    Access refundedOwner = new Access(false, true, false, false, true);
    assertThat(new DenyWinsChain(List.of(Rule.REFUNDED, Rule.OWNER)).decide(refundedOwner))
        .isEqualTo(new Vote.Allow("owner"));
  }

  @Test
  void closedByDefault() {
    Access stranger = new Access(false, false, false, false, false);
    assertThat(new DenyWinsChain(List.of(Rule.ADMIN, Rule.PURCHASED)).decide(stranger))
        .isEqualTo(new Vote.Deny("closed by default"));
    assertThat(new DenyWinsChain(List.of()).decide(stranger))
        .isEqualTo(new Vote.Deny("closed by default"));
  }

  @Test
  void aThrowingRuleFailsShut() {
    Rule broken =
        a -> {
          throw new IllegalStateException("bug");
        };
    Access buyer = new Access(false, false, false, true, false);
    assertThat(new DenyWinsChain(List.of(Rule.PURCHASED, broken)).decide(buyer))
        .isEqualTo(new Vote.Deny("rule failed"));
  }
}
