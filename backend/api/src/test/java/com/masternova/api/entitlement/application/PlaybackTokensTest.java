package com.masternova.api.entitlement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.entitlement.application.PlaybackTokens.PlaybackToken;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The token alone: what it binds, and everything that makes it worthless. No Spring. */
class PlaybackTokensTest {

  static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
  static final String IP = "203.0.113.7";

  final PlaybackTokens tokens = new PlaybackTokens("a-playback-secret-that-is-long-enough-0123");
  final PlaybackToken granted =
      new PlaybackToken(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW.plus(Duration.ofMinutes(5)));
  final String token = tokens.issue(granted, IP);

  @Test
  void itAuthorizesThisLectureFromThisIpUntilItExpires() {
    assertThat(tokens.verify(token, granted.lectureId(), IP, NOW)).contains(granted);
    assertThat(tokens.verify(token, granted.lectureId(), IP, NOW.plus(Duration.ofSeconds(299))))
        .isPresent();
  }

  @Test
  void anotherNetworkAnotherLectureOrTooLateIsNothing() {
    assertThat(tokens.verify(token, granted.lectureId(), "198.51.100.9", NOW)).isEmpty();
    assertThat(tokens.verify(token, UUID.randomUUID(), IP, NOW)).isEmpty();
    assertThat(tokens.verify(token, granted.lectureId(), IP, granted.expiresAt())).isEmpty();
  }

  @Test
  void aForgedOrMangledTokenIsNothing() {
    PlaybackTokens otherKey = new PlaybackTokens("another-playback-secret-long-enough-01234");
    assertThat(otherKey.verify(token, granted.lectureId(), IP, NOW)).isEmpty();
    assertThat(tokens.verify(token + "x", granted.lectureId(), IP, NOW)).isEmpty();
    assertThat(tokens.verify("", granted.lectureId(), IP, NOW)).isEmpty();
    assertThat(tokens.verify(null, granted.lectureId(), IP, NOW)).isEmpty();
  }

  @Test
  void theIpIsSignedButNeverCarried() {
    assertThat(token).doesNotContain(IP);
    String payload =
        new String(
            java.util.Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))),
            java.nio.charset.StandardCharsets.UTF_8);
    assertThat(payload).doesNotContain(IP).contains(granted.userId().toString());
  }
}
