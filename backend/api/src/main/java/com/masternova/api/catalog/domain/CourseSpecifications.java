package com.masternova.api.catalog.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * The catalog's filters as SPECIFICATIONS: each method returns one small, named predicate, and
 * callers compose them with Spring Data's {@code Specification.allOf / and / or / not}.
 *
 * <ul>
 *   <li>⭐ The force: six optional facets that combine freely (2⁶ combinations) plus a visibility
 *       rule every list must apply. Adding a facet = adding a leaf here and one line where the
 *       search is assembled ({@code CourseSearch}); the repository and the query method never
 *       change.
 *   <li>⭐ Each leaf is a lambda over the JPA Criteria API: {@code (root, query, cb) -> Predicate}.
 *       {@code root} is the Course row, {@code cb} builds SQL expressions. Hibernate turns the
 *       composed tree into ONE {@code WHERE} clause.
 *   <li>Attribute names are strings ("status", "price"): a typo fails at query time, not compile
 *       time — {@code CourseSpecificationsIT} runs every leaf against Postgres for that reason.
 *       (The JPA static metamodel, {@code Course_.status}, would make them compile-time checked;
 *       not worth an annotation processor for ten names.)
 * </ul>
 *
 * <p>Pattern note: patterns/docs/08-specification.md.
 */
@DesignPattern(
    value = Pattern.SPECIFICATION,
    role = "ConcreteSpecification (leaves)",
    note = "patterns/docs/08-specification.md")
public final class CourseSpecifications {

  private CourseSpecifications() {}

  public static Specification<Course> published() {
    return (root, query, cb) -> cb.equal(root.get("status"), CourseStatus.PUBLISHED);
  }

  public static Specification<Course> byInstructor(UUID instructorId) {
    Objects.requireNonNull(instructorId, "instructorId");
    return (root, query, cb) -> cb.equal(root.get("instructorId"), instructorId);
  }

  /**
   * ⭐ {@code category.id IN (…)}: comparing the FK's id never joins the category table — Hibernate
   * reads it straight from {@code course.category_id}.
   */
  public static Specification<Course> inCategories(Collection<UUID> categoryIds) {
    Set<UUID> ids = Set.copyOf(categoryIds);
    return (root, query, cb) -> root.get("category").get("id").in(ids);
  }

  public static Specification<Course> atLevels(Collection<CourseLevel> levels) {
    Set<CourseLevel> wanted = Set.copyOf(levels);
    return (root, query, cb) -> root.get("level").in(wanted);
  }

  public static Specification<Course> inLanguage(String language) {
    Objects.requireNonNull(language, "language");
    return (root, query, cb) -> cb.equal(root.get("language"), language);
  }

  /** ⭐ A path into the embedded value object: {@code price.amountMinor} → {@code price_minor}. */
  public static Specification<Course> free() {
    return (root, query, cb) -> cb.equal(root.get("price").get("amountMinor"), 0L);
  }

  public static Specification<Course> paid() {
    return Specification.not(free());
  }

  public static Specification<Course> ratedAtLeast(BigDecimal minimum) {
    Objects.requireNonNull(minimum, "minimum");
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ratingAverage"), minimum);
  }

  /**
   * Case-insensitive "title contains". ⭐ The user's text is a LITERAL: {@code %} and {@code _} are
   * LIKE wildcards, so they're escaped — searching "100%" must not mean "starts with 100". (It's a
   * bind parameter either way: no SQL injection is possible, only wrong results.)
   */
  public static Specification<Course> titleContains(String text) {
    String pattern = "%" + escapeLike(text.strip().toLowerCase(Locale.ROOT)) + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
  }

  /**
   * ⭐ The visibility rule as SQL — the twin of {@link Viewer#canSee(Course)}. Composed INTO the
   * query, so an invisible course is never loaded at all (and can't leak through a forgotten
   * check).
   */
  public static Specification<Course> visibleTo(Viewer viewer) {
    return switch (viewer) {
      case Viewer.Admin _ -> Specification.unrestricted();
      case Viewer.Member(UUID id) -> published().or(byInstructor(id));
      case Viewer.Anonymous _ -> published();
    };
  }

  static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
