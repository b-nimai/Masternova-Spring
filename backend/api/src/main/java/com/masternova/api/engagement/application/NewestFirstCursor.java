package com.masternova.api.engagement.application;

import com.masternova.api.platform.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * "Continue after this row" for engagement's newest-first lists (reviews, questions): the row's
 * {@code created_at} and id, as an OPAQUE token (API conventions §2, ADR-0009). Only one order
 * exists, so unlike catalog's cursor it carries no sort.
 */
record NewestFirstCursor(Instant at, UUID id) {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  String encode() {
    return ENCODER.encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @return the cursor, or {@code null} for the first page
   * @throws ValidationException (400 {@code INVALID_CURSOR}) for anything this API didn't issue
   */
  static NewestFirstCursor decode(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      String[] parts = new String(DECODER.decode(token), StandardCharsets.UTF_8).split("\\|", -1);
      if (parts.length != 2) {
        throw invalid();
      }
      return new NewestFirstCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw invalid();
    }
  }

  private static ValidationException invalid() {
    return ValidationException.of(
        "cursor", "INVALID_CURSOR", "The cursor is not valid for this list; start again.");
  }
}
