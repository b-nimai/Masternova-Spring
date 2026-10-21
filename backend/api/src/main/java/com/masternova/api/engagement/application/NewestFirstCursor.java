package com.masternova.api.engagement.application;

import com.masternova.api.platform.CursorPage;
import com.masternova.api.platform.CursorTokens;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * "Continue after this row" for engagement's newest-first lists (reviews, questions): the row's
 * {@code created_at} and id, as an opaque token (API conventions §2, wire format in {@link
 * CursorTokens}). Only one order exists, so unlike catalog's cursor it carries no sort.
 */
record NewestFirstCursor(Instant at, UUID id) {

  /** The controllers' {@code @Max}; checked again here, where {@code limit + 1} is computed. */
  static final int MAX_LIMIT = 50;

  String encode() {
    return CursorTokens.encode(at.toString(), id.toString());
  }

  /**
   * @return the cursor, or {@code null} for the first page
   * @throws com.masternova.api.platform.ValidationException (400 {@code INVALID_CURSOR})
   */
  static NewestFirstCursor decode(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    String[] parts = CursorTokens.decode(token, 2);
    try {
      return new NewestFirstCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw CursorTokens.invalid();
    }
  }

  /** What a repository is asked for: the rows after the cursor, one more than a page. */
  interface Fetch<T> {
    List<T> rows(Instant beforeAt, UUID beforeId, int limit);
  }

  /**
   * ⭐ The one keyset page of engagement: fetch {@code limit + 1} rows after the cursor — the extra
   * row only answers "is there a next page?" — and continue after the last row kept.
   */
  static <T> CursorPage<T> page(
      String token, int limit, Fetch<T> fetch, Function<T, Instant> at, Function<T, UUID> id) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("a page holds 1.." + MAX_LIMIT + " rows: " + limit);
    }
    NewestFirstCursor after = decode(token);
    List<T> rows =
        fetch.rows(after == null ? null : after.at(), after == null ? null : after.id(), limit + 1);
    if (rows.size() <= limit) {
      return new CursorPage<>(rows, null);
    }
    T last = rows.get(limit - 1);
    return new CursorPage<>(
        List.copyOf(rows.subList(0, limit)),
        new NewestFirstCursor(at.apply(last), id.apply(last)).encode());
  }
}
