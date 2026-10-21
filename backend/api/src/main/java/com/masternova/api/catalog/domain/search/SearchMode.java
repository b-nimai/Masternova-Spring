package com.masternova.api.catalog.domain.search;

/** How a search ranks: by words, by meaning, or by both fused (the default). */
public enum SearchMode {
  KEYWORD,
  SEMANTIC,
  HYBRID
}
