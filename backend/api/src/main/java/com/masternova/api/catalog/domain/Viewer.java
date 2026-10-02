package com.masternova.api.catalog.domain;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Objects;
import java.util.UUID;

/**
 * Who is looking at the catalog — the input of the VISIBILITY rule (docs/lld/catalog.md §2: "the
 * same draft is a 404 for a stranger, a 200 for its author and a 200 for an admin").
 *
 * <p>⭐ A sealed interface of records: the three kinds of viewer are closed, so every {@code switch}
 * over them is exhaustive — a fourth kind (say, a reviewer in Phase 6) is a compile error at every
 * rule that must decide about it, not a silent default.
 *
 * <p>The rule exists in TWO forms that must agree: {@link #canSee(Course)} in memory (the course
 * page, which loads by slug) and {@link CourseSpecifications#visibleTo(Viewer)} as SQL (lists).
 * {@code CourseSpecificationsIT} runs both over the same rows and compares.
 */
@DesignPattern(
    value = Pattern.SPECIFICATION,
    role = "in-memory twin of visibleTo",
    note = "patterns/docs/08-specification.md")
public sealed interface Viewer {

  record Anonymous() implements Viewer {}

  record Member(UUID id) implements Viewer {
    public Member {
      Objects.requireNonNull(id, "id");
    }
  }

  record Admin(UUID id) implements Viewer {
    public Admin {
      Objects.requireNonNull(id, "id");
    }
  }

  static Viewer anonymous() {
    return new Anonymous();
  }

  /** In memory: published courses for everyone, every state for the owner and for admins. */
  default boolean canSee(Course course) {
    return switch (this) {
      case Admin _ -> true;
      case Member(UUID id) -> course.isPublished() || course.isOwnedBy(id);
      case Anonymous _ -> course.isPublished();
    };
  }
}
