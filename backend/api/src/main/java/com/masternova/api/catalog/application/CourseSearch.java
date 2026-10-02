package com.masternova.api.catalog.application;

import static com.masternova.api.catalog.domain.CourseSpecifications.atLevels;
import static com.masternova.api.catalog.domain.CourseSpecifications.free;
import static com.masternova.api.catalog.domain.CourseSpecifications.inCategories;
import static com.masternova.api.catalog.domain.CourseSpecifications.inLanguage;
import static com.masternova.api.catalog.domain.CourseSpecifications.paid;
import static com.masternova.api.catalog.domain.CourseSpecifications.published;
import static com.masternova.api.catalog.domain.CourseSpecifications.ratedAtLeast;
import static com.masternova.api.catalog.domain.CourseSpecifications.titleContains;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseLevel;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * What a visitor asked the public catalog for. Every facet is optional: {@code null} (or an empty
 * set) means "not filtered by this". An empty search is "every published course".
 *
 * <p>Nullable components, not {@code Optional} ones: {@code Optional} is a return type, not a field
 * type (Java note 05 §9).
 *
 * <p>⭐ {@link #toSpecification} is the ONE place facets become Specifications: a present facet adds
 * a leaf, an absent one adds nothing, and {@code allOf} ANDs whatever is there. One line per facet,
 * no {@code if} per combination, and the repository never changes.
 */
@DesignPattern(
    value = Pattern.SPECIFICATION,
    role = "Client (composes the leaves present)",
    note = "patterns/docs/08-specification.md")
public record CourseSearch(
    String text,
    String categorySlug,
    Set<CourseLevel> levels,
    String language,
    PriceFilter price,
    BigDecimal minRating) {

  public enum PriceFilter {
    FREE,
    PAID
  }

  public CourseSearch {
    text = blankToNull(text);
    categorySlug = blankToNull(categorySlug);
    language = blankToNull(language);
    levels = levels == null ? Set.of() : Set.copyOf(levels);
  }

  public static CourseSearch everything() {
    return new CourseSearch(null, null, Set.of(), null, null, null);
  }

  /**
   * @param categoryIds the requested category and its children, already resolved from the slug
   *     (ignored when no category was asked for)
   */
  public Specification<Course> toSpecification(Collection<UUID> categoryIds) {
    List<Specification<Course>> specs = new ArrayList<>();
    specs.add(published()); // the public catalog: published only, always
    if (text != null) {
      specs.add(titleContains(text));
    }
    if (categorySlug != null) {
      specs.add(inCategories(categoryIds));
    }
    if (!levels.isEmpty()) {
      specs.add(atLevels(levels));
    }
    if (language != null) {
      specs.add(inLanguage(language));
    }
    if (price != null) {
      specs.add(price == PriceFilter.FREE ? free() : paid());
    }
    if (minRating != null) {
      specs.add(ratedAtLeast(minRating));
    }
    return Specification.allOf(specs); // ⭐ AND of the leaves present
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
