package com.masternova.api.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Who teaches a course, as the catalog knows it: the user's id plus a SNAPSHOT of their display
 * name, so the most-read query never joins identity's tables (docs/lld/catalog.md §3).
 */
public record Instructor(UUID id, String name) {

  public Instructor {
    Objects.requireNonNull(id, "id");
    name = Objects.requireNonNull(name, "name").strip();
    if (name.isEmpty() || name.length() > 100) {
      throw new IllegalArgumentException("an instructor name needs 1–100 characters");
    }
  }
}
