package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.application.CurriculumService.Result;
import com.masternova.api.catalog.domain.Lecture;
import com.masternova.api.catalog.domain.LectureKind;
import com.masternova.api.catalog.domain.Section;
import java.util.List;
import java.util.UUID;

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

  /** The author's view of a section: unlike the public course page, it carries each asset id. */
  public record SectionResponse(String id, String title, List<LectureResponse> lectures) {
    static SectionResponse from(Section s) {
      return new SectionResponse(
          s.id().toString(), s.title(), s.lectures().stream().map(LectureResponse::from).toList());
    }
  }

  public record LectureResponse(
      String id,
      String title,
      LectureKind kind,
      boolean preview,
      int durationSeconds,
      String assetId) {
    static LectureResponse from(Lecture l) {
      return new LectureResponse(
          l.id().toString(),
          l.title(),
          l.kind(),
          l.isPreview(),
          l.duration().seconds(),
          l.assetId().map(UUID::toString).orElse(null));
    }
  }
}
