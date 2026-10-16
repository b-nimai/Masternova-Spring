package com.masternova.api.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.enrollment.application.BeaconTokens.Beacon;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The beacon credential: one user, one lecture, one network, two minutes. No Spring. */
class BeaconTokensTest {

  static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
  static final String IP = "203.0.113.7";

  final BeaconTokens tokens = new BeaconTokens("a-beacon-secret-that-is-long-enough-012345");
  final UUID user = UUID.randomUUID();
  final UUID lecture = UUID.randomUUID();
  final Instant expiresAt = NOW.plus(Duration.ofMinutes(2));
  final String token = tokens.issue(user, lecture, expiresAt, IP);

  @Test
  void itNamesItsUserAndLectureUntilItExpires() {
    assertThat(tokens.verify(token, IP, NOW)).contains(new Beacon(user, lecture));
    assertThat(tokens.verify(token, IP, expiresAt.minusSeconds(1))).isPresent();
    assertThat(tokens.verify(token, IP, expiresAt)).isEmpty();
  }

  @Test
  void anotherNetworkAnotherKeyOrAMangledTokenIsNothing() {
    assertThat(tokens.verify(token, "198.51.100.9", NOW)).isEmpty();
    assertThat(new BeaconTokens("another-beacon-secret-long-enough-0123456").verify(token, IP, NOW))
        .isEmpty();
    assertThat(tokens.verify(token + "x", IP, NOW)).isEmpty();
    assertThat(tokens.verify("", IP, NOW)).isEmpty();
  }
}
