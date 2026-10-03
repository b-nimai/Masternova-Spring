package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.web.dto.CourseDetailResponse.SectionResponse;
import java.util.List;

/** The curriculum after an edit, with the version the editor sends next. */
public record CurriculumResponse(
    long version, int lectureCount, int totalDurationSeconds, List<SectionResponse> sections) {

  public static CurriculumResponse from(Course c) {
    return new CurriculumResponse(
        c.version(),
        c.lectureCount(),
        c.totalDuration().seconds(),
        c.sections().stream().map(SectionResponse::from).toList());
  }
}
