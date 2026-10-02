package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CourseCatalogService;
import com.masternova.api.catalog.application.CourseDuplicationService;
import com.masternova.api.catalog.application.CoursePage;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.web.dto.CourseDetailResponse;
import com.masternova.api.catalog.web.dto.CourseSummary;
import com.masternova.api.catalog.web.dto.CursorPage;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.IdempotencyKeyRequired;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** An instructor's own courses. The role rules live on the services (@PreAuthorize). */
@RestController
@RequestMapping("/api/v1/instructor/courses")
class InstructorCourseController {

  private final CourseCatalogService catalog;
  private final CourseDuplicationService duplication;

  InstructorCourseController(CourseCatalogService catalog, CourseDuplicationService duplication) {
    this.catalog = catalog;
    this.duplication = duplication;
  }

  /**
   * ⭐ Constraints directly on @RequestParam: Spring MVC 6.1+ validates handler-method parameters by
   * itself (no @Validated on the class) and reports them as a 400 VALIDATION_FAILED.
   */
  @GetMapping
  CursorPage<CourseSummary> mine(
      CurrentUser me,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
      @RequestParam(required = false) @Size(max = 300) String cursor) {
    CoursePage page = catalog.mine(me.id(), cursor, limit);
    return CursorPage.of(page.items(), page.nextCursor(), CourseSummary::from);
  }

  /**
   * ⭐ An unsafe write with no version to guard it, so an {@code Idempotency-Key} is REQUIRED (API
   * conventions §4): a double-clicked "Duplicate" replays the first 201 instead of making two
   * copies.
   */
  @PostMapping("/{id}/duplicate")
  @IdempotencyKeyRequired
  ResponseEntity<CourseDetailResponse> duplicate(@PathVariable UUID id, CurrentUser me) {
    Course copy = duplication.duplicate(id, Viewers.of(me));
    return ResponseEntity.created(URI.create("/api/v1/courses/" + copy.slug()))
        .body(CourseDetailResponse.from(copy));
  }
}
