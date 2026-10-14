package com.masternova.patterns.facade;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The subsystem classes a caller would otherwise have to know and call in the right order. (Real
 * ones: catalog's {@code CatalogApi}, the cached {@code EntitlementRepository}, the {@code
 * PolicyChain}.)
 */
public final class Subsystems {

  private Subsystems() {}

  /** What the course is: who teaches it, is it free, which lectures are previews. */
  public record CourseFacts(String courseId, String instructorId, boolean free, Set<String> previews) {}

  public static final class Catalog {
    private final Map<String, CourseFacts> byLecture;

    public Catalog(Map<String, CourseFacts> byLecture) {
      this.byLecture = Map.copyOf(byLecture);
    }

    public Optional<CourseFacts> courseOfLecture(String lectureId) {
      return Optional.ofNullable(byLecture.get(lectureId));
    }
  }

  public static final class Purchases {
    private final Set<String> owned; // "user:course"
    private int lookups;

    public Purchases(Set<String> owned) {
      this.owned = Set.copyOf(owned);
    }

    public boolean owns(String userId, String courseId) {
      lookups++;
      return owned.contains(userId + ":" + courseId);
    }

    public int lookups() {
      return lookups;
    }
  }

  /** The rules, given everything they need up front. */
  public static final class Rules {
    public boolean allows(String userId, CourseFacts course, String lectureId, boolean owns) {
      return userId.equals(course.instructorId())
          || course.free()
          || course.previews().contains(lectureId)
          || owns;
    }
  }
}
