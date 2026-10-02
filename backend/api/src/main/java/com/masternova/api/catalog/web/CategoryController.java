package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CourseCatalogService;
import com.masternova.api.catalog.web.dto.CategoryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

  private final CourseCatalogService catalog;

  CategoryController(CourseCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  List<CategoryResponse> tree() {
    return CategoryResponse.tree(catalog.categories());
  }
}
