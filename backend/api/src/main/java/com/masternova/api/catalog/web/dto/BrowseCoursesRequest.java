package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.application.CourseSearch;
import com.masternova.api.catalog.application.CourseSearch.PriceFilter;
import com.masternova.api.catalog.domain.CourseLevel;
import com.masternova.api.catalog.domain.CourseSort;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

/**
 * The public catalog's query string, bound to a record:
 *
 * <pre>
 *   GET /api/v1/courses?q=kubernetes&amp;category=devops-cloud&amp;level=BEGINNER&amp;level=ADVANCED
 *                     &amp;language=en&amp;price=FREE&amp;minRating=4&amp;sort=NEWEST&amp;limit=20&amp;cursor=…
 * </pre>
 *
 * <p>⭐ Spring binds query parameters to a record through its constructor (repeated {@code level}
 * parameters become the set). Bean Validation then runs on it ({@code @Valid}), and a failure is
 * the usual 400 {@code VALIDATION_FAILED} with one entry per field. An unknown enum value ({@code
 * sort=BEST}) is a binding error, reported the same way.
 */
public record BrowseCoursesRequest(
    @Size(max = 100) String q,
    @Size(max = 80) String category,
    Set<CourseLevel> level,
    @Pattern(regexp = "[a-z]{2}") String language,
    PriceFilter price,
    @DecimalMin("0") @DecimalMax("5") BigDecimal minRating,
    CourseSort sort,
    @Min(1) @Max(50) Integer limit,
    @Size(max = 300) String cursor) {

  public static final int DEFAULT_LIMIT = 20;

  public CourseSearch toSearch() {
    return new CourseSearch(q, category, level, language, price, minRating);
  }

  public CourseSort sortOrDefault() {
    return sort == null ? CourseSort.NEWEST : sort;
  }

  public int limitOrDefault() {
    return limit == null ? DEFAULT_LIMIT : limit;
  }
}
