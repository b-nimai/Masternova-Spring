package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CourseCatalogService;
import com.masternova.api.catalog.application.CoursePage;
import com.masternova.api.catalog.web.dto.BrowseCoursesRequest;
import com.masternova.api.catalog.web.dto.CourseDetailResponse;
import com.masternova.api.catalog.web.dto.CourseSummary;
import com.masternova.api.catalog.web.dto.CursorPage;
import com.masternova.api.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public catalog: browse and the course page. Both are public routes (CatalogConfig). */
@RestController
@RequestMapping("/api/v1/courses")
class CourseController {

  private final CourseCatalogService catalog;

  CourseController(CourseCatalogService catalog) {
    this.catalog = catalog;
  }

  /** ⭐ No @RequestParam per facet: the query string binds to one validated record. */
  @GetMapping
  CursorPage<CourseSummary> browse(@Valid BrowseCoursesRequest request) {
    CoursePage page =
        catalog.browse(
            request.toSearch(),
            request.sortOrDefault(),
            request.cursor(),
            request.limitOrDefault());
    return CursorPage.of(page.items(), page.nextCursor(), CourseSummary::from);
  }

  /** Public, but the viewer matters: an owner (or an admin) also sees a draft. */
  @GetMapping("/{slug}")
  CourseDetailResponse get(@PathVariable String slug, Optional<CurrentUser> viewer) {
    return CourseDetailResponse.from(catalog.bySlug(slug, Viewers.of(viewer)));
  }
}
