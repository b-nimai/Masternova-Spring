package com.masternova.api.enrollment.web.dto;

import com.masternova.api.enrollment.application.LearningQueries.CourseProgress;
import java.util.List;
import java.util.UUID;

/**
 * One course's progress for its learner, read through the buffer (a second old at most).
 *
 * @param continueLectureId null when not enrolled, or every lecture is complete
 */
public record CourseProgressResponse(
    UUID courseId,
    boolean enrolled,
    int percentComplete,
    List<UUID> completedLectureIds,
    UUID continueLectureId,
    int continuePositionSeconds) {

  public static CourseProgressResponse from(CourseProgress p) {
    return new CourseProgressResponse(
        p.courseId(),
        p.enrolled(),
        p.percent(),
        p.completedLectureIds().stream().sorted().toList(), // a stable order for clients and tests
        p.continueLectureId().orElse(null),
        p.continuePositionSeconds());
  }
}
