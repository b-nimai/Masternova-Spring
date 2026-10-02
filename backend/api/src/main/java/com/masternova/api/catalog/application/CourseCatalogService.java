package com.masternova.api.catalog.application;

import static com.masternova.api.catalog.domain.CourseSpecifications.byInstructor;
import static com.masternova.api.catalog.domain.CourseSpecifications.keysetBound;

import com.masternova.api.catalog.domain.Category;
import com.masternova.api.catalog.domain.CategoryRepository;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.CourseSort;
import com.masternova.api.catalog.domain.Viewer;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.ValidationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalog's read use cases: browse, a course page, an instructor's own list, the category tree.
 * Every method is {@code readOnly}: Hibernate skips dirty checking and flushing.
 *
 * <p>⭐ The service decides what's LOADED (fetch graphs, initialising the curriculum inside the
 * transaction); the controller only maps to DTOs. With open-in-view off, a lazy association touched
 * in the controller would throw.
 */
@Service
public class CourseCatalogService {

  private final CourseRepository courses;
  private final CategoryRepository categories;

  CourseCatalogService(CourseRepository courses, CategoryRepository categories) {
    this.courses = courses;
    this.categories = categories;
  }

  /** The public catalog: published courses matching the search, one keyset page. */
  @Transactional(readOnly = true)
  public CoursePage browse(CourseSearch search, CourseSort sort, String cursor, int limit) {
    if (!sort.isPublic()) {
      throw ValidationException.of(
          "sort", "UNSUPPORTED_SORT", "This list can't be sorted that way.");
    }
    Set<UUID> categoryIds = Set.of();
    if (search.categorySlug() != null) {
      categoryIds = categoryTree(search.categorySlug());
      if (categoryIds.isEmpty()) {
        return CoursePage.empty(); // an unknown category is an empty list, not a 404 (LLD §8)
      }
    }
    return page(search.toSpecification(categoryIds), sort, cursor, limit);
  }

  /** The instructor's own courses, every status, most recently edited first. */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional(readOnly = true)
  public CoursePage mine(UUID instructorId, String cursor, int limit) {
    return page(byInstructor(instructorId), CourseSort.RECENTLY_UPDATED, cursor, limit);
  }

  /**
   * The course page: course + category + curriculum in two statements (note 12 §5).
   *
   * @throws NotFoundException for a missing course AND for one this viewer may not see — the same
   *     404, so the endpoint can't be used to discover drafts
   */
  @Transactional(readOnly = true)
  public Course bySlug(String slug, Viewer viewer) {
    Course course =
        courses
            .findWithCurriculumBySlug(slug)
            .filter(viewer::canSee)
            .orElseThrow(() -> new NotFoundException("Course", slug));
    // ⭐ Load the lectures NOW, inside the transaction: touching one section's lectures makes
    //    @BatchSize fetch every section's lectures in one statement.
    course.sections().forEach(section -> section.lectures().size());
    return course;
  }

  @Transactional(readOnly = true)
  public List<Category> categories() {
    return categories.findAll(Sort.by("position"));
  }

  // ------------------------------------------------------------------ keyset paging

  private CoursePage page(Specification<Course> spec, CourseSort sort, String cursor, int limit) {
    Specification<Course> query = spec.and(fetchingCategory());
    ScrollPosition position = ScrollPosition.keyset(); // the first page
    if (cursor != null) {
      CourseCursor after = CourseCursor.decode(cursor, sort);
      position = after.toScrollPosition();
      query = query.and(keysetBound(sort, after.key())); // ⭐ lets Postgres SEEK to the cursor
    }

    // ⭐ ONE statement: WHERE spec AND key <= :k AND (key, id) after the cursor, ORDER BY key, id,
    //    LIMIT limit+1 (the extra row tells hasNext), with the category fetched by the same join.
    ScrollPosition from = position;
    Window<Course> window =
        courses.findBy(query, q -> q.sortBy(sort.toSort()).limit(limit).scroll(from));

    List<Course> items = window.getContent();
    String next = window.hasNext() ? CourseCursor.after(sort, items.getLast()).encode() : null;
    return new CoursePage(items, next);
  }

  /**
   * A FETCH PLAN expressed as a Specification: join the category into the list query, so a page of
   * 20 courses over 12 categories is 1 statement, not 1 + 12, and the summaries can be mapped after
   * the transaction without a {@code LazyInitializationException}.
   *
   * <p>⭐ Why not {@code q.project("category")}: in Spring Data JPA 4.1 it adds a fetch graph to
   * {@code all()}, {@code page()} and {@code stream()}, but {@code scroll()} builds its query
   * through a separate delegate that ignores it (found by {@code CatalogApiIT}; note 12 §5). A
   * count query can't fetch, hence the result-type check.
   */
  private static Specification<Course> fetchingCategory() {
    return (root, query, cb) -> {
      Class<?> resultType = query.getResultType();
      if (resultType != Long.class && resultType != long.class) {
        root.fetch("category");
      }
      return cb.conjunction(); // no restriction: this spec only shapes WHAT is loaded
    };
  }

  /** A category slug → its id and its children's ids (browsing a root includes its children). */
  private Set<UUID> categoryTree(String slug) {
    return categories.findBySlugOrParentSlug(slug, slug).stream()
        .map(Category::id)
        .collect(Collectors.toUnmodifiableSet());
  }
}
