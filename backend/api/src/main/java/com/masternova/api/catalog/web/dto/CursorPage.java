package com.masternova.api.catalog.web.dto;

import java.util.List;
import java.util.function.Function;

/**
 * The list envelope of API conventions §2: {@code {"items": [...], "nextCursor": "…" | null}} — no
 * total. Generic so every catalog list has the same shape; it moves to platform when a second
 * module pages a list.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

  public static <E, T> CursorPage<T> of(List<E> items, String nextCursor, Function<E, T> mapper) {
    return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor);
  }
}
