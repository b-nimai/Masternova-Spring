package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.Category;

/** A category as it appears on a course: enough to link to it. */
public record CategoryRef(String slug, String name) {

  public static CategoryRef from(Category category) {
    return new CategoryRef(category.slug(), category.name());
  }
}
