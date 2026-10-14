package com.masternova.api.entitlement.web.dto;

import com.masternova.api.entitlement.application.CourseAccess;
import java.util.List;

/** The course page's locks: the course-wide decision (its call to action) and one per lecture. */
public record CourseAccessResponse(
    String courseId, AccessResponse course, List<LectureAccessResponse> lectures) {

  public record LectureAccessResponse(String lectureId, AccessResponse access) {}

  public static CourseAccessResponse from(CourseAccess a) {
    return new CourseAccessResponse(
        a.courseId().toString(),
        AccessResponse.from(a.course()),
        a.lectures().stream()
            .map(
                l ->
                    new LectureAccessResponse(
                        l.lectureId().toString(), AccessResponse.from(l.decision())))
            .toList());
  }
}
