package com.masternova.api.catalog.application;

import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseSort;
import com.masternova.api.platform.ValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.ScrollPosition;

/**
 * "Continue after this row": the sort it belongs to, that row's sort key, and its id — encoded as
 * an OPAQUE token (API conventions §2, ADR-0009 §5).
 *
 * <pre>
 *   base64url( "NEWEST|2026-10-02T10:00:00Z|3f2c…-uuid" )
 * </pre>
 *
 * <ul>
 *   <li>⭐ Opaque: clients pass it back and never parse it, so the format can change freely.
 *   <li>⭐ It carries its SORT: a NEWEST cursor sent with {@code sort=PRICE_LOW} is a 400 instead of
 *       a silently wrong page.
 *   <li>⭐ The key is TYPED per sort (an {@code Instant}, a {@code BigDecimal}, a {@code long}).
 *       Spring Data's own keyset map would come back from JSON as strings and compare wrongly.
 *   <li>Not signed: a forged cursor only moves the caller's OWN position in a list the visibility
 *       rule already filtered. There's nothing to protect.
 * </ul>
 */
record CourseCursor(CourseSort sort, Comparable<?> key, UUID id) {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  static CourseCursor after(CourseSort sort, Course last) {
    return new CourseCursor(sort, sort.keyOf(last), last.id());
  }

  String encode() {
    String raw = sort.name() + "|" + format(key) + "|" + id;
    return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @throws ValidationException (400) for anything that isn't a cursor this API issued for {@code
   *     expected}: not base64, wrong shape, unparsable key, or a different sort
   */
  static CourseCursor decode(String token, CourseSort expected) {
    try {
      String[] parts = new String(DECODER.decode(token), StandardCharsets.UTF_8).split("\\|", -1);
      if (parts.length != 3 || !parts[0].equals(expected.name())) {
        throw invalid();
      }
      return new CourseCursor(expected, parse(expected, parts[1]), UUID.fromString(parts[2]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw invalid(); // ⭐ Base64, UUID, number and date parsers all throw these on garbage
    }
  }

  /** ⭐ The Spring Data keyset position: "rows after (key, id)" in the sort's direction. */
  ScrollPosition toScrollPosition() {
    Map<String, Object> keys = new LinkedHashMap<>();
    keys.put(sort.property(), key);
    keys.put("id", id);
    return ScrollPosition.forward(keys);
  }

  private static String format(Comparable<?> key) {
    return switch (key) {
      case BigDecimal d -> d.toPlainString();
      default -> key.toString(); // Instant → ISO-8601, Long → digits
    };
  }

  private static Comparable<?> parse(CourseSort sort, String text) {
    return switch (sort) {
      case NEWEST, RECENTLY_UPDATED -> Instant.parse(text);
      case HIGHEST_RATED -> new BigDecimal(text);
      case PRICE_LOW, PRICE_HIGH -> Long.valueOf(text);
    };
  }

  private static ValidationException invalid() {
    return ValidationException.of(
        "cursor", "INVALID_CURSOR", "The cursor is not valid for this list; start again.");
  }
}
