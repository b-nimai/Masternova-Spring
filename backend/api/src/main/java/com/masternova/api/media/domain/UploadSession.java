package com.masternova.api.media.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
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
 * One resumable upload: the provider's multipart upload id and our {@link PartPlan}. ⭐ Never which
 * parts arrived — that is asked of the provider (ADR-0014).
 *
 * <p>The Context of the State pattern: every event asks {@link #state} first. An {@code OPEN}
 * session past {@code expiresAt} already IS expired ({@link #state} says so) even before the
 * sweeper (7.6) aborts the provider's upload and records it.
 */
@Entity
@Table(name = "upload_session")
@DesignPattern(value = Pattern.STATE, role = "Context", note = "patterns/docs/02-state.md")
public class UploadSession {

  @Id private UUID id;

  @Column(name = "asset_id", nullable = false, unique = true)
  private UUID assetId;

  @Column(name = "provider_upload_id", nullable = false, length = 1024)
  private String providerUploadId;

  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "part_size_bytes", nullable = false)
  private long partSizeBytes;

  @Column(name = "part_count", nullable = false)
  private int partCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UploadStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Version private long version;

  protected UploadSession() {} // JPA

  private UploadSession(
      UUID id,
      UUID assetId,
      String providerUploadId,
      String objectKey,
      PartPlan plan,
      Instant now,
      Instant expiresAt) {
    this.id = Objects.requireNonNull(id);
    this.assetId = Objects.requireNonNull(assetId);
    this.providerUploadId = Objects.requireNonNull(providerUploadId);
    this.objectKey = Objects.requireNonNull(objectKey);
    this.sizeBytes = plan.size();
    this.partSizeBytes = plan.partSize();
    this.partCount = plan.partCount();
    this.status = UploadStatus.OPEN;
    this.createdAt = now;
    this.expiresAt = expiresAt;
  }

  public static UploadSession open(
      UUID id,
      UUID assetId,
      String providerUploadId,
      String objectKey,
      PartPlan plan,
      Instant now,
      Instant expiresAt) {
    if (!expiresAt.isAfter(now)) {
      throw new IllegalArgumentException("a session must expire in the future");
    }
    return new UploadSession(id, assetId, providerUploadId, objectKey, plan, now, expiresAt);
  }

  /** ⭐ The EFFECTIVE state at {@code now}: past its expiry, an open session is expired. */
  public UploadState state(Instant now) {
    if (status == UploadStatus.OPEN && !now.isBefore(expiresAt)) {
      return new UploadState.Expired();
    }
    return UploadState.of(status);
  }

  public PartPlan plan() {
    return new PartPlan(sizeBytes, partSizeBytes, partCount);
  }

  // Transitions. The service asks state(now) first and calls these only on PERFORM; each one
  // re-checks so a misuse fails loudly instead of corrupting the row.

  public void markCompleted(Instant now) {
    requireOpen();
    status = UploadStatus.COMPLETED;
    completedAt = now;
  }

  public void markAborted() {
    requireOpen();
    status = UploadStatus.ABORTED;
  }

  public void markExpired() {
    requireOpen();
    status = UploadStatus.EXPIRED;
  }

  private void requireOpen() {
    if (status != UploadStatus.OPEN) {
      throw new IllegalStateException("upload " + id + " is " + status);
    }
  }

  public UUID id() {
    return id;
  }

  public UUID assetId() {
    return assetId;
  }

  public String providerUploadId() {
    return providerUploadId;
  }

  public String objectKey() {
    return objectKey;
  }

  public UploadStatus status() {
    return status;
  }

  public Instant expiresAt() {
    return expiresAt;
  }
}
