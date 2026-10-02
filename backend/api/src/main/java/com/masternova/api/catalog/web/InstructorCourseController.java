package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CourseCatalogService;
import com.masternova.api.catalog.application.CoursePage;
import com.masternova.api.catalog.web.dto.CourseSummary;
import com.masternova.api.catalog.web.dto.CursorPage;
import com.masternova.api.identity.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** An instructor's own courses. The role rule lives on the service (@PreAuthorize). */
@RestController
@RequestMapping("/api/v1/instructor/courses")
class InstructorCourseController {

  private final CourseCatalogService catalog;

  InstructorCourseController(CourseCatalogService catalog) {
    this.catalog = catalog;
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
}
