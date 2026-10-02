package com.masternova.api.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.catalog.domain.CourseSort;
import com.masternova.api.platform.ValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.KeysetScrollPosition;

/** The opaque cursor: typed round trips, and everything that isn't ours is a 400. */
class CourseCursorTest {

  static final UUID ID = UUID.fromString("3f2c0000-0000-4000-8000-000000000001");

  @Test
  void eachSortRoundTripsItsTypedKey() {
    var newest =
        new CourseCursor(CourseSort.NEWEST, Instant.parse("2026-10-02T10:00:00.123456Z"), ID);
    var rated = new CourseCursor(CourseSort.HIGHEST_RATED, new BigDecimal("4.50"), ID);
    var cheap = new CourseCursor(CourseSort.PRICE_LOW, 49900L, ID);

    assertThat(CourseCursor.decode(newest.encode(), CourseSort.NEWEST)).isEqualTo(newest);
    assertThat(CourseCursor.decode(rated.encode(), CourseSort.HIGHEST_RATED)).isEqualTo(rated);
    assertThat(CourseCursor.decode(cheap.encode(), CourseSort.PRICE_LOW)).isEqualTo(cheap);
  }

  @Test
  void itIsOpaqueToClients() {
    String token = new CourseCursor(CourseSort.PRICE_LOW, 49900L, ID).encode();

    assertThat(token).matches("[A-Za-z0-9_-]+"); // URL-safe, no padding: fits in a query string
  }

  @Test
  void aCursorFromAnotherSortIsRejected() {
    String newest = new CourseCursor(CourseSort.NEWEST, Instant.EPOCH, ID).encode();

    assertThatThrownBy(() -> CourseCursor.decode(newest, CourseSort.PRICE_LOW))
        .isInstanceOfSatisfying(
            ValidationException.class,
            e -> assertThat(e.errors().getFirst().code()).isEqualTo("INVALID_CURSOR"));
  }

  @Test
  void garbageIsRejectedNotCrashedOn() {
    for (String bad :
        new String[] {
          "%%%", // not base64url
          b64("NEWEST|2026-10-02T10:00:00Z"), // two parts
          b64("NEWEST|yesterday|" + ID), // unparsable key
          b64("PRICE_LOW|cheap|" + ID),
          b64("NEWEST|2026-10-02T10:00:00Z|not-a-uuid"),
          b64("NEWEST|2026-10-02T10:00:00Z|" + ID + "|extra")
        }) {
      assertThatThrownBy(
              () -> CourseCursor.decode(bad, bad.equals("%%%") ? CourseSort.NEWEST : sortOf(bad)))
          .as(bad)
          .isInstanceOf(ValidationException.class);
    }
  }

  @Test
  void itBecomesASpringDataKeysetPosition() {
    var position =
        (KeysetScrollPosition) new CourseCursor(CourseSort.PRICE_HIGH, 100L, ID).toScrollPosition();

    assertThat(position.getKeys()).isEqualTo(Map.of("price.amountMinor", 100L, "id", ID));
    assertThat(position.scrollsForward()).isTrue();
  }

  private static String b64(String raw) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static CourseSort sortOf(String token) {
    String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    return CourseSort.valueOf(raw.substring(0, raw.indexOf('|')));
  }
}
