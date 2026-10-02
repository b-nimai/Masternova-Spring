package com.masternova.kernel.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnsubscribeTokensTest {

  static final String SECRET = "test-secret-0123456789abcdef-0123456789";
  static final UUID USER = UUID.fromString("7d444840-9dc0-11d1-b245-5ffdce74fad2");
  static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
  static final Instant EXPIRES = NOW.plus(UnsubscribeTokens.TTL);

  final UnsubscribeTokens tokens = new UnsubscribeTokens(SECRET);

  @Test
  void aTokenWeIssuedVerifiesToTheSameUserAndCategory() {
    String token = tokens.issue(USER, NotificationCategory.PRODUCT_NEWS, EXPIRES);

    assertThat(token).matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"); // URL-safe: no escaping needed
    assertThat(tokens.verify(token, NOW))
        .contains(new UnsubscribeTokens.Unsubscribe(USER, NotificationCategory.PRODUCT_NEWS));
  }

  @Test
  void anExpiredTokenIsRejected() {
    String token = tokens.issue(USER, NotificationCategory.PRODUCT_NEWS, EXPIRES);

    assertThat(tokens.verify(token, EXPIRES)).isEmpty();
    assertThat(tokens.verify(token, EXPIRES.minusSeconds(1))).isPresent();
  }

  @Test
  void changingTheCategoryInsideTheTokenBreaksTheSignature() {
    String token = tokens.issue(USER, NotificationCategory.PRODUCT_NEWS, EXPIRES);
    String signature = token.substring(token.indexOf('.') + 1);
    String forgedPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                (USER + ".ENGAGEMENT." + EXPIRES.getEpochSecond()).getBytes()); // ⭐ the attack

    assertThat(tokens.verify(forgedPayload + "." + signature, NOW)).isEmpty();
  }

  @Test
  void aTokenSignedWithAnotherSecretIsRejected() {
    String token =
        new UnsubscribeTokens("another-secret-0123456789abcdef-01234567")
            .issue(USER, NotificationCategory.PRODUCT_NEWS, EXPIRES);

    assertThat(tokens.verify(token, NOW)).isEmpty();
  }

  @Test
  void garbageNeverThrowsItIsJustInvalid() {
    for (String garbage : new String[] {null, "", ".", "abc", "a.b.c", "!!!.???", "abc.def"}) {
      assertThat(tokens.verify(garbage, NOW)).as("%s", garbage).isEmpty();
    }
  }

  @Test
  void aShortSecretIsRefusedAtConstruction() {
    assertThatThrownBy(() -> new UnsubscribeTokens("too-short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }
}
