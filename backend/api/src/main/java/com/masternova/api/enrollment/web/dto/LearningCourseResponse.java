package com.masternova.api.enrollment.web.dto;

import com.masternova.api.enrollment.application.LearningQueries.LearningCourse;
import java.time.Instant;
import java.util.UUID;

/**
 * A card on "My Learning". The counts are the rollups (≤ 30 s behind, ADR-0022).
 *
 * @param continueLectureId the lecture Continue opens; null = everything watched (or nothing to)
 * @param lastActivityAt null = enrolled, never watched
 */
public record LearningCourseResponse(
    UUID courseId,
    String slug,
    String title,
    String instructorName,
    int completedLectures,
    int totalLectures,
    int percentComplete,
    Instant enrolledAt,
    Instant lastActivityAt,
    UUID continueLectureId) {

  public static LearningCourseResponse from(LearningCourse c) {
    return new LearningCourseResponse(
        c.course().courseId(),
        c.course().slug(),
        c.course().title(),
        c.course().instructorName(),
        c.completedLectures(),
        c.trackableLectures(),
        c.percent(),
        c.enrolledAt(),
        c.lastActivityAt().orElse(null),
        c.continueLectureId().orElse(null));
  }
}
