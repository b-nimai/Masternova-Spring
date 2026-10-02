package com.masternova.api.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reference data, read-only in practice (Flyway seeds it). */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  Optional<Category> findBySlug(String slug);

  /** A root and its children (one level down) — what "browse this category" means. */
  List<Category> findBySlugOrParentSlug(String slug, String parentSlug);
}
