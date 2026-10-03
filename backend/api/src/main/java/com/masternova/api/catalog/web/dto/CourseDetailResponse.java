package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseLevel;
import com.masternova.api.catalog.domain.CourseStatus;
import com.masternova.api.catalog.domain.Lecture;
import com.masternova.api.catalog.domain.LectureKind;
import com.masternova.api.catalog.domain.Section;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The course page. ⭐ No {@code assetId}: which lectures may be WATCHED is entitlement's decision
 * (Phase 8), behind a short-lived playback token — the catalog only describes the curriculum.
 */
public record CourseDetailResponse(
    String id,
    String slug,
    String title,
    String subtitle,
    String description,
    CourseLevel level,
    String language,
    CourseStatus status,
    long priceMinor,
    String currency,
    BigDecimal ratingAverage,
    int ratingCount,
    int enrollmentCount,
    int lectureCount,
    int totalDurationSeconds,
    String instructorName,
    CategoryRef category,
    Instant publishedAt,
    boolean priceSet,
    long version,
    List<SectionResponse> sections) {

  public record SectionResponse(String id, String title, List<LectureResponse> lectures) {
    static SectionResponse from(Section s) {
      return new SectionResponse(
          s.id().toString(), s.title(), s.lectures().stream().map(LectureResponse::from).toList());
    }
  }

  public record LectureResponse(
      String id, String title, LectureKind kind, boolean preview, int durationSeconds) {
    static LectureResponse from(Lecture l) {
      return new LectureResponse(
          l.id().toString(), l.title(), l.kind(), l.isPreview(), l.duration().seconds());
    }
  }

  public static CourseDetailResponse from(Course c) {
    return new CourseDetailResponse(
        c.id().toString(),
        c.slug(),
        c.title(),
        c.subtitle().orElse(null),
        c.description(),
        c.level(),
        c.language(),
        c.status(),
        c.price().amountMinor(),
        c.price().currency().getCurrencyCode(),
        c.ratingAverage(),
        c.ratingCount(),
        c.enrollmentCount(),
        c.lectureCount(),
        c.totalDuration().seconds(),
        c.instructor().name(),
        CategoryRef.from(c.category()),
        c.publishedAt().orElse(null),
        c.priceSetAt().isPresent(),
        c.version() == null ? 0 : c.version(), // ⭐ editors send it back as expectedVersion
        c.sections().stream().map(SectionResponse::from).toList());
  }
}
