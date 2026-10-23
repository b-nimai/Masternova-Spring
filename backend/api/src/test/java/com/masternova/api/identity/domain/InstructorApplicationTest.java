package com.masternova.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.identity.domain.InstructorApplication.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstructorApplicationTest {

  private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
  private static final Instant LATER = NOW.plusSeconds(3600);
  private static final UUID APPLICANT = UUID.randomUUID();
  private static final UUID ADMIN = UUID.randomUUID();

  @Test
  void anApplicationStartsPendingWithATrimmedOptionalPitch() {
    InstructorApplication a = InstructorApplication.submit(APPLICANT, "  Kubernetes  ", NOW);

    assertThat(a.status()).isEqualTo(Status.PENDING);
    assertThat(a.pitch()).isEqualTo("Kubernetes");
    assertThat(InstructorApplication.submit(APPLICANT, "   ", NOW).pitch()).isNull();
    assertThatThrownBy(() -> InstructorApplication.submit(APPLICANT, "x".repeat(501), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decidingTwiceTheSameWayIsANoOpThatKeepsTheFirstDecision() {
    InstructorApplication a = InstructorApplication.submit(APPLICANT, null, NOW);
    assertThat(a.approve(ADMIN, NOW)).isTrue();
    // a double click, a retried request: false, so the caller grants nothing the second time
    assertThat(a.approve(UUID.randomUUID(), LATER)).isFalse();

    assertThat(a.status()).isEqualTo(Status.APPROVED);
    assertThat(a.decidedAt()).isEqualTo(NOW);
  }

  @Test
  void aDecisionCannotBeFlippedTheOtherWay() {
    InstructorApplication approved = InstructorApplication.submit(APPLICANT, null, NOW);
    approved.approve(ADMIN, NOW);
    InstructorApplication rejected = InstructorApplication.submit(APPLICANT, null, NOW);
    rejected.reject(ADMIN, NOW);

    assertThatThrownBy(() -> approved.reject(ADMIN, LATER))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> rejected.approve(ADMIN, LATER))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aRejectedApplicantResubmitsAndIsPendingAgain() {
    InstructorApplication a = InstructorApplication.submit(APPLICANT, null, NOW);
    a.reject(ADMIN, NOW);
    a.resubmit("Now with a course outline", LATER);

    assertThat(a.status()).isEqualTo(Status.PENDING);
    assertThat(a.appliedAt()).isEqualTo(LATER);
    assertThat(a.decidedAt()).isNull();
    assertThatThrownBy(() -> a.resubmit(null, LATER)).isInstanceOf(IllegalStateException.class);
  }
}
