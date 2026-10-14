package com.masternova.api.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What another module may know about a course in order to DECIDE about it (entitlement, Phase 8):
 * who teaches it, whether it's on sale, its price, and each lecture's preview flag and video. A
 * snapshot, read in one go, so a decision never queries in the middle of its rules.
 *
 * <p>⭐ No catalog entity leaves the module: this record (and {@link CatalogApi}) is the contract.
 */
public record CourseFacts(
    UUID courseId,
    UUID instructorId,
    Availability availability,
    long priceMinor,
    boolean priceSet,
    List<LectureFacts> lectures) {

  /**
   * The lifecycle, collapsed to what an outsider needs: DRAFT and IN_REVIEW are both unpublished.
   */
  public enum Availability {
    UNPUBLISHED,
    PUBLISHED,
    ARCHIVED
  }

  /**
   * @param video the lecture's media asset; empty for a text lecture or before an upload
   */
  public record LectureFacts(UUID lectureId, boolean preview, Optional<UUID> video) {
    public LectureFacts {
      Objects.requireNonNull(lectureId, "lectureId");
      Objects.requireNonNull(video, "video");
    }
  }

  public CourseFacts {
    Objects.requireNonNull(courseId, "courseId");
    Objects.requireNonNull(instructorId, "instructorId");
    Objects.requireNonNull(availability, "availability");
    lectures = List.copyOf(lectures);
  }

  /** ⭐ Price 0 AND priced: 0 alone also means "nobody has set a price yet" (catalog LLD §3). */
  public boolean isFree() {
    return priceSet && priceMinor == 0;
  }

  public Optional<LectureFacts> lecture(UUID lectureId) {
    return lectures.stream().filter(l -> l.lectureId().equals(lectureId)).findFirst();
  }
}
