package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.application.CurriculumService.Result;
import com.masternova.api.catalog.web.dto.CourseDetailResponse.SectionResponse;
import java.util.List;

/**
 * The curriculum after an edit: the version the editor sends next, and whether its undo/redo
 * buttons are enabled.
 */
public record CurriculumResponse(
    long version,
    boolean canUndo,
    boolean canRedo,
    int lectureCount,
    int totalDurationSeconds,
    List<SectionResponse> sections) {

  public static CurriculumResponse from(Result result) {
    var c = result.course();
    return new CurriculumResponse(
        c.version(),
        result.canUndo(),
        result.canRedo(),
        c.lectureCount(),
        c.totalDuration().seconds(),
        c.sections().stream().map(SectionResponse::from).toList());
  }
}
