package com.masternova.api.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A learner asking to teach. The INSTRUCTOR role is granted by an admin, never chosen: signup's
 * "Instructor" tab (or the account page) files this, and an admin approves or rejects it. One per
 * user (the id IS the user's), so re-applying after a rejection resubmits the same row.
 *
 * <p>Three states, two transitions out of PENDING: an enum and guarded methods are enough — the
 * course's state machine (note 05) earns its pattern with seven states; this does not.
 */
@Entity
@Table(name = "instructor_application")
public class InstructorApplication {

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED
  }

  public static final int PITCH_MAX = 500;

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(length = PITCH_MAX)
  private String pitch;

  @Column(name = "applied_at", nullable = false)
  private Instant appliedAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Version private Long version;

  protected InstructorApplication() {} // for Hibernate

  private InstructorApplication(UUID userId, String pitch, Instant now) {
    this.userId = userId;
    this.status = Status.PENDING;
    this.pitch = pitch;
    this.appliedAt = now;
  }

  public static InstructorApplication submit(UUID userId, String pitch, Instant now) {
    return new InstructorApplication(
        Objects.requireNonNull(userId, "userId"), normalise(pitch), Objects.requireNonNull(now));
  }

  /** Apply again after a rejection (or after the role was later taken away). */
  public void resubmit(String pitch, Instant now) {
    if (status == Status.PENDING) {
      throw new IllegalStateException("already pending");
    }
    this.status = Status.PENDING;
    this.pitch = normalise(pitch);
    this.appliedAt = now;
    this.decidedAt = null;
    this.decidedBy = null;
  }

  /**
   * ⭐ Idempotent: an admin's double click (or a retried request) approves once and keeps the first
   * decision. The caller grants the role in the same transaction, and only when this returns {@code
   * true}: a repeated approve must not undo a later demotion.
   *
   * @return whether this call approved it (false: it already was)
   */
  public boolean approve(UUID adminId, Instant now) {
    if (status == Status.APPROVED) {
      return false;
    }
    decide(Status.APPROVED, adminId, now);
    return true;
  }

  /** Idempotent like approve; rejecting an approved application is a different act (a demotion). */
  public void reject(UUID adminId, Instant now) {
    if (status == Status.REJECTED) {
      return;
    }
    decide(Status.REJECTED, adminId, now);
  }

  private void decide(Status outcome, UUID adminId, Instant now) {
    if (status != Status.PENDING) {
      throw new IllegalStateException("not pending: " + status);
    }
    this.status = outcome;
    this.decidedBy = Objects.requireNonNull(adminId, "adminId");
    this.decidedAt = Objects.requireNonNull(now, "now");
  }

  private static String normalise(String pitch) {
    if (pitch == null || pitch.isBlank()) {
      return null;
    }
    String trimmed = pitch.strip();
    if (trimmed.length() > PITCH_MAX) {
      throw new IllegalArgumentException("pitch is longer than " + PITCH_MAX);
    }
    return trimmed;
  }

  public UUID userId() {
    return userId;
  }

  public Status status() {
    return status;
  }

  public boolean isPending() {
    return status == Status.PENDING;
  }

  public String pitch() {
    return pitch;
  }

  public Instant appliedAt() {
    return appliedAt;
  }

  public Instant decidedAt() {
    return decidedAt;
  }
}
