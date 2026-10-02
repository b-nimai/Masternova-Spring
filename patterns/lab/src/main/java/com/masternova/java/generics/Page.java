package com.masternova.java.generics;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One page of a cursor-paginated list: {@code { "items": [...], "nextCursor": "..." | null }} —
 * exactly the shape of the API convention (docs/api/conventions.md §2).
 *
 * <p>⭐ A GENERIC RECORD: {@code Page<CourseSummary>}, {@code Page<Review>} — one type, any item.
 */
public record Page<T>(List<T> items, String nextCursor) {

  public Page {
    items = List.copyOf(items); // immutable snapshot (note 01 §5)
  }

  /** An empty last page — of any type: {@code Page<Course> p = Page.empty();} */
  public static <T> Page<T> empty() {
    return new Page<>(List.of(), null);
  }

  /**
   * Builds a page from a query that fetched {@code limit + 1} rows — the keyset trick: if the
   * extra row exists there IS a next page, and the last row on this page gives the cursor.
   *
   * @param rows rows from the database, already sorted, at most {@code limit + 1}
   * @param cursorOf how to turn an item into an opaque cursor (e.g. its sort key + id)
   */
  public static <T> Page<T> fromRows(
      List<? extends T> rows, int limit, Function<? super T, String> cursorOf) {
    Objects.requireNonNull(cursorOf, "cursorOf");
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be at least 1, was " + limit);
    }
    if (rows.size() <= limit) {
      return new Page<>(List.copyOf(rows), null); // no extra row → this is the last page
    }
    List<T> pageItems = List.copyOf(rows.subList(0, limit));
    return new Page<>(pageItems, cursorOf.apply(pageItems.getLast()));
  }

  /** Same page, items transformed — e.g. entities → DTOs in a controller. */
  public <R> Page<R> map(Function<? super T, ? extends R> fn) {
    // ⭐ <R> on an INSTANCE method of a generic class: T comes from the class, R from the call.
    List<R> mapped = items.stream().<R>map(fn).toList();
    return new Page<>(mapped, nextCursor);
  }

  public boolean hasNext() {
    return nextCursor != null;
  }
}
