package com.masternova.java.sealed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.java.sealed.PaymentOutcome.Captured;
import com.masternova.java.sealed.PaymentOutcome.Failed;
import com.masternova.java.sealed.PaymentOutcome.Pending;
import com.masternova.java.valueobject.Money;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentOutcomesTest {

  private static final Instant NOON = Instant.parse("2026-10-02T12:00:00Z");

  @Test
  void messageForEachShape() {
    assertThat(PaymentOutcomes.message(new Captured("pay_1", Money.of(149_900, "INR"))))
        .isEqualTo("Payment successful: INR 1499.00");
    assertThat(PaymentOutcomes.message(new Failed("BANK_TIMEOUT", true)))
        .isEqualTo("Payment didn't go through (BANK_TIMEOUT). Please try again.");
    assertThat(PaymentOutcomes.message(new Failed("CARD_DECLINED", false)))
        .isEqualTo("Payment declined (CARD_DECLINED). Try another payment method.");
    assertThat(PaymentOutcomes.message(new Pending("pay_2", NOON)))
        .isEqualTo("Waiting for your bank to confirm…");
  }

  @Test
  void nestedRecordPatternSpotsAFreeEnrollment() {
    assertThat(PaymentOutcomes.message(new Captured("pay_0", Money.zero("INR"))))
        .isEqualTo("You're enrolled — this one was free!");
  }

  @Test
  void retriesOnlyTemporaryFailuresAndOnlyThreeTimes() {
    assertThat(PaymentOutcomes.shouldRetry(new Failed("BANK_TIMEOUT", true), 0)).isTrue();
    assertThat(PaymentOutcomes.shouldRetry(new Failed("BANK_TIMEOUT", true), 3)).isFalse();
    assertThat(PaymentOutcomes.shouldRetry(new Failed("CARD_DECLINED", false), 0)).isFalse();
    assertThat(PaymentOutcomes.shouldRetry(new Captured("p", Money.of(1, "INR")), 0)).isFalse();
    assertThat(PaymentOutcomes.shouldRetry(new Pending("p", NOON), 0)).isFalse();
  }

  @Test
  void caseNullTurnsNullIntoAnOrdinaryCase() {
    assertThat(PaymentOutcomes.statusLabel(null)).isEqualTo("UNKNOWN");
    assertThat(PaymentOutcomes.statusLabel(new Pending("p", NOON))).isEqualTo("PENDING");
  }

  @Test
  void withoutCaseNullASwitchThrowsOnNull() {
    assertThatThrownBy(() -> PaymentOutcomes.message(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void instanceofPatternBindsOnlyWhenItMatches() {
    Pending pending = new Pending("p", NOON);

    assertThat(PaymentOutcomes.isExpired(pending, NOON.plusSeconds(1))).isTrue();
    assertThat(PaymentOutcomes.isExpired(pending, NOON.minusSeconds(1))).isFalse();
    assertThat(PaymentOutcomes.isExpired(new Failed("X", false), NOON)).isFalse();
  }

  @Test
  void theCompilerKnowsEveryPermittedSubtype() {
    // Reflection view of `sealed`: the class file records the closed list of subtypes.
    assertThat(PaymentOutcome.class.isSealed()).isTrue();
    assertThat(PaymentOutcome.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(Captured.class, Failed.class, Pending.class);
  }
}
