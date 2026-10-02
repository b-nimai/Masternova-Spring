package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.Category;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** One node of the two-level category tree. */
public record CategoryResponse(String slug, String name, List<CategoryResponse> children) {

  /**
   * Builds the tree from the flat list (already ordered by position). Grouping children by their
   * parent's id needs no extra queries: {@code parent().map(Category::id)} reads the id from the
   * lazy proxy without loading it.
   */
  public static List<CategoryResponse> tree(Collection<Category> all) {
    Map<UUID, List<Category>> childrenByParent =
        all.stream()
            .filter(c -> !c.isRoot())
            .collect(Collectors.groupingBy(c -> c.parent().orElseThrow().id()));
    return all.stream()
        .filter(Category::isRoot)
        .map(
            root ->
                new CategoryResponse(
                    root.slug(),
                    root.name(),
                    childrenByParent.getOrDefault(root.id(), List.of()).stream()
                        .map(child -> new CategoryResponse(child.slug(), child.name(), List.of()))
                        .toList()))
        .toList();
  }
}
