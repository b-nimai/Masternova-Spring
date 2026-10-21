package com.masternova.api.platform;

import java.util.List;
import java.util.function.Function;

/**
 * The list envelope of API conventions §2: {@code {"items": [...], "nextCursor": "…" | null}} — no
 * total. Generic so every list has the same shape: catalog's since Phase 5, in platform since Phase
 * 11 (engagement's reviews and questions are the second module to page a list).
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

  public static <E, T> CursorPage<T> of(List<E> items, String nextCursor, Function<E, T> mapper) {
    return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor);
  }
}
