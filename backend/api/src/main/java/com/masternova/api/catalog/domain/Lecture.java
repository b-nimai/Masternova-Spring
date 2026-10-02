package com.masternova.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One lecture — an entity INSIDE the Course aggregate. Created only through {@link
 * Course#addLecture}, so the course's rollups (lecture count, total duration) can't go stale.
 */
@Entity
@Table(name = "lecture")
public class Lecture {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "section_id")
  private Section section;

  @Column(nullable = false, length = 120)
  private String title;

  @Enumerated(EnumType.STRING) // ⭐ STRING, never ORDINAL: reordering the enum would corrupt rows
  @Column(nullable = false, length = 20)
  private LectureKind kind;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private boolean preview;

  // ⭐ no @Convert here: LectureDurationConverter is autoApply — the domain never names it
  @Column(name = "duration_seconds", nullable = false)
  private LectureDuration duration;

  @Column(name = "asset_id")
  private UUID assetId;

  protected Lecture() {} // for Hibernate

  /**
   * ⭐ PROTOTYPE copy constructor: a new lecture in {@code section} with this one's content. New id;
   * the media {@code assetId} is SHARED on purpose — the transcoded video is immutable and can be
   * gigabytes, so the copy points at the same asset instead of duplicating it.
   */
  Lecture(Section section, Lecture source) {
    this.id = UUID.randomUUID();
    this.section = section;
    this.title = source.title;
    this.kind = source.kind;
    this.position = source.position;
    this.preview = source.preview;
    this.duration = source.duration; // a value object: immutable, so sharing it is a copy
    this.assetId = source.assetId; // ⭐ shallow ON PURPOSE
  }

  Lecture(
      Section section,
      String title,
      LectureKind kind,
      int position,
      boolean preview,
      LectureDuration duration,
      UUID assetId) {
    this.id = UUID.randomUUID();
    this.section = section;
    this.title = requireTitle(title);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.position = position;
    this.preview = preview;
    this.duration = Objects.requireNonNull(duration, "duration");
    this.assetId = assetId;
  }

  static String requireTitle(String title) {
    String t = Objects.requireNonNull(title, "title").strip();
    if (t.isEmpty() || t.length() > 120) {
      throw new IllegalArgumentException("a title needs 1–120 characters");
    }
    return t;
  }

  public UUID id() {
    return id;
  }

  public Section section() {
    return section;
  }

  public String title() {
    return title;
  }

  public LectureKind kind() {
    return kind;
  }

  public int position() {
    return position;
  }

  public boolean isPreview() {
    return preview;
  }

  public LectureDuration duration() {
    return duration;
  }

  public Optional<UUID> assetId() {
    return Optional.ofNullable(assetId);
  }
}
