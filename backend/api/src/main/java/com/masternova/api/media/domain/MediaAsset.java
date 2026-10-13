package com.masternova.api.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One uploaded video and what the pipeline learned about it. The api creates it and marks it {@code
 * UPLOADED}; the worker owns every later step (media LLD §9, "who writes what").
 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId; // an id, not a @ManyToOne User: identity's entity is not ours to map

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MediaKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MediaStatus status;

  @Column(name = "original_filename", nullable = false)
  private String originalFilename;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  private Integer width;
  private Integer height;

  @Column(name = "failure_code", length = 40)
  private String failureCode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "ready_at")
  private Instant readyAt;

  @Version private long version;

  protected MediaAsset() {} // JPA

  private MediaAsset(
      UUID id, UUID ownerId, String originalFilename, String contentType, long size, Instant now) {
    this.id = Objects.requireNonNull(id);
    this.ownerId = Objects.requireNonNull(ownerId);
    this.kind = MediaKind.VIDEO;
    this.status = MediaStatus.AWAITING_UPLOAD;
    this.originalFilename = originalFilename;
    this.contentType = contentType;
    this.sizeBytes = size;
    this.createdAt = now;
  }

  public static MediaAsset awaitingUpload(
      UUID id, UUID ownerId, String originalFilename, String contentType, long size, Instant now) {
    return new MediaAsset(id, ownerId, originalFilename, contentType, size, now);
  }

  /** The source is complete in storage: the pipeline may start (via {@code MediaUploaded}). */
  public void markUploaded() {
    requireStatus(MediaStatus.AWAITING_UPLOAD);
    status = MediaStatus.UPLOADED;
  }

  /** The instructor gave up on the upload; the asset never gets a source. */
  public void markAbandoned() {
    requireStatus(MediaStatus.AWAITING_UPLOAD);
    status = MediaStatus.FAILED;
    failureCode = "UPLOAD_ABANDONED";
  }

  private void requireStatus(MediaStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("asset " + id + " is " + status + ", not " + expected);
    }
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerId.equals(userId);
  }

  public UUID id() {
    return id;
  }

  public UUID ownerId() {
    return ownerId;
  }

  public MediaStatus status() {
    return status;
  }

  public long sizeBytes() {
    return sizeBytes;
  }

  public String originalFilename() {
    return originalFilename;
  }

  public Optional<Integer> durationSeconds() {
    return Optional.ofNullable(durationSeconds);
  }

  public Optional<String> failureCode() {
    return Optional.ofNullable(failureCode);
  }
}
