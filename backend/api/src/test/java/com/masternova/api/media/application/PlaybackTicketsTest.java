package com.masternova.api.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaybackTicketsTest {

  static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
  final PlaybackTickets tickets = new PlaybackTickets("a-test-secret-that-is-long-enough-0123");
  final UUID asset = UUID.randomUUID();
  final UUID user = UUID.randomUUID();

  @Test
  void aTicketWorksForItsAssetUntilItExpires() {
    String t = tickets.issue(asset, user, NOW.plusSeconds(60));

    assertThat(tickets.verify(t, asset, NOW))
        .hasValueSatisfying(v -> assertThat(v.userId()).isEqualTo(user));
    assertThat(tickets.verify(t, asset, NOW.plusSeconds(60))).isEmpty(); // expired
  }

  @Test
  void aTicketForOneAssetDoesNotOpenAnother() {
    String t = tickets.issue(asset, user, NOW.plusSeconds(60));
    assertThat(tickets.verify(t, UUID.randomUUID(), NOW)).isEmpty();
  }

  @Test
  void forgedOrMangledTicketsAreRejectedWithoutAHint() {
    String t = tickets.issue(asset, user, NOW.plusSeconds(60));
    String otherKey =
        new PlaybackTickets("another-secret-that-is-long-enough-0123")
            .issue(asset, user, NOW.plusSeconds(60));

    assertThat(tickets.verify(otherKey, asset, NOW)).isEmpty();
    assertThat(tickets.verify(t.substring(0, t.length() - 2) + "xx", asset, NOW)).isEmpty();
    assertThat(tickets.verify("not.a.ticket", asset, NOW)).isEmpty();
    assertThat(tickets.verify("%%%.%%%", asset, NOW)).isEmpty();
    assertThat(tickets.verify(null, asset, NOW)).isEmpty();
  }

  @Test
  void aShortSecretIsRefused() {
    assertThatThrownBy(() -> new PlaybackTickets("short"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
