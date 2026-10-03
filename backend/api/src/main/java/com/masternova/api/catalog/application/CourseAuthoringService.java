package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Category;
import com.masternova.api.catalog.domain.CategoryRepository;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseLevel;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.Slugs;
import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.identity.IdentityApi;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.ValidationException;
import com.masternova.kernel.money.Money;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The wizard's content writes: start a course, change its details, confirm its price. Every write
 * takes the {@code expectedVersion} the client last saw (ADR-0010); the curriculum has its own
 * service (commands + undo).
 */
@Service
public class CourseAuthoringService {

  /** What the wizard's first step collects. */
  public record NewCourse(String title, String categorySlug, CourseLevel level, String language) {}

  /** The details step. */
  public record Details(
      String title,
      String subtitle,
      String description,
      String categorySlug,
      CourseLevel level,
      String language) {}

  private final CourseRepository courses;
  private final CategoryRepository categories;
  private final CourseAccess access;
  private final IdentityApi identity;
  private final Clock clock;

  CourseAuthoringService(
      CourseRepository courses,
      CategoryRepository categories,
      CourseAccess access,
      IdentityApi identity,
      Clock clock) {
    this.courses = courses;
    this.categories = categories;
    this.access = access;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * A new, empty DRAFT owned by the caller. Its price is 0 but NOT confirmed, so the publish gate
   * asks for a pricing decision (PRICE_NOT_SET).
   */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Course create(NewCourse request, UUID authorId) {
    // ⭐ through identity's PUBLIC API — catalog never reads identity's tables
    String name =
        identity.displayName(authorId).orElseThrow(() -> new NotFoundException("User", authorId));
    Course course =
        Course.draft(
            Slugs.forTitle(request.title()),
            request.title(),
            "",
            request.level(),
            request.language(),
            Money.zero(Course.CATALOG_CURRENCY.getCurrencyCode()),
            category(request.categorySlug()),
            new Instructor(authorId, name),
            clock.instant());
    return courses.save(course);
  }

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional(readOnly = true)
  public Course get(UUID courseId, Viewer actor) {
    return access.forAuthoring(courseId, actor);
  }

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Course updateDetails(UUID courseId, long expectedVersion, Details d, Viewer actor) {
    Course course = access.forEditing(courseId, actor, expectedVersion);
    course.changeDetails(
        d.title(),
        d.subtitle(),
        d.description(),
        d.level(),
        d.language(),
        category(d.categorySlug()),
        clock.instant());
    return course; // flushed at commit: UPDATE … WHERE version = expectedVersion
  }

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public Course confirmPrice(UUID courseId, long expectedVersion, long priceMinor, Viewer actor) {
    Course course = access.forEditing(courseId, actor, expectedVersion);
    course.confirmPrice(
        Money.of(priceMinor, Course.CATALOG_CURRENCY.getCurrencyCode()), clock.instant());
    return course;
  }

  private Category category(String slug) {
    return categories
        .findBySlug(slug)
        .orElseThrow(
            () -> ValidationException.of("categorySlug", "UNKNOWN_CATEGORY", "No such category."));
  }
}
