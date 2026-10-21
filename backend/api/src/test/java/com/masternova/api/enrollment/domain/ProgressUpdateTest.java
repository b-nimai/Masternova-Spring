package com.masternova.api.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

/**
 * ⭐ The merge is a semilattice join — the property the whole write-back design rests on (ADR-0022).
 * Random heartbeat sequences, merged in every order, in pieces, with duplicates, always give the
 * one same answer; {@code max} never goes down. No Spring, no DB: a seeded {@link Random} per
 * repetition, so a failure names the seed that reproduces it.
 */
class ProgressUpdateTest {

  static final UUID USER = UUID.randomUUID();
  static final UUID LECTURE = UUID.randomUUID();
  static final Instant T0 = Instant.parse("2026-10-16T10:00:00Z");

  @RepeatedTest(500)
  void anyOrderAnyGroupingAnyDuplicatesGiveOneAnswer(RepetitionInfo run) {
    Random random = new Random(run.getCurrentRepetition());
    List<ProgressUpdate> beats = heartbeats(random);
    ProgressUpdate expected = foldLeft(beats);

    List<ProgressUpdate> shuffled = new ArrayList<>(beats);
    shuffled.addAll(beats.subList(0, random.nextInt(beats.size()) + 1)); // a replayed flush
    Collections.shuffle(shuffled, random);
    assertThat(foldLeft(shuffled)).isEqualTo(expected); // commutative + idempotent

    int cut = random.nextInt(shuffled.size());
    ProgressUpdate partial = // two partial batches, merged later (dirty + flushing)
        foldLeft(shuffled.subList(0, Math.max(cut, 1)))
            .merge(foldLeft(shuffled.subList(cut, shuffled.size())));
    assertThat(partial).isEqualTo(expected); // associative

    assertThat(expected.maxPositionSeconds())
        .isEqualTo(beats.stream().mapToInt(ProgressUpdate::maxPositionSeconds).max().orElseThrow());
    assertThat(expected.lastPositionSeconds()).isLessThanOrEqualTo(expected.maxPositionSeconds());
  }

  @RepeatedTest(200)
  void maxNeverGoesDownWhateverArrives(RepetitionInfo run) {
    Random random = new Random(-run.getCurrentRepetition());
    ProgressUpdate acc = null;
    for (ProgressUpdate beat : heartbeats(random)) {
      ProgressUpdate next = acc == null ? beat : acc.merge(beat);
      if (acc != null) {
        assertThat(next.maxPositionSeconds()).isGreaterThanOrEqualTo(acc.maxPositionSeconds());
      }
      acc = next;
    }
  }

  @Test
  void lastIsTheNewestHeartbeatEvenWhenItRewinds() {
    ProgressUpdate watched = ProgressUpdate.heartbeat(USER, LECTURE, 600, T0);
    ProgressUpdate rewound = ProgressUpdate.heartbeat(USER, LECTURE, 30, T0.plusSeconds(15));

    ProgressUpdate merged = rewound.merge(watched); // the order of arrival doesn't matter
    assertThat(merged.lastPositionSeconds()).isEqualTo(30); // resume where they are now
    assertThat(merged.maxPositionSeconds()).isEqualTo(600); // but they did get to 10:00
    assertThat(merged.at()).isEqualTo(T0.plusSeconds(15));
  }

  @Test
  void aTieInTimeGoesToTheLargerPosition() {
    ProgressUpdate a = ProgressUpdate.heartbeat(USER, LECTURE, 100, T0);
    ProgressUpdate b = ProgressUpdate.heartbeat(USER, LECTURE, 120, T0);
    assertThat(a.merge(b)).isEqualTo(b.merge(a));
    assertThat(a.merge(b).lastPositionSeconds()).isEqualTo(120);
  }

  @Test
  void mergeByRowLeavesOneUpdatePerRow() {
    UUID other = UUID.randomUUID();
    List<ProgressUpdate> rows =
        ProgressUpdate.mergeByRow(
            List.of(
                ProgressUpdate.heartbeat(USER, LECTURE, 10, T0),
                ProgressUpdate.heartbeat(USER, other, 5, T0),
                ProgressUpdate.heartbeat(USER, LECTURE, 25, T0.plusSeconds(15))));
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst().lastPositionSeconds()).isEqualTo(25);
  }

  @Test
  void itRefusesWhatNoPlayerCouldSend() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ProgressUpdate.heartbeat(USER, LECTURE, -1, T0));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ProgressUpdate.heartbeat(
                    USER, LECTURE, ProgressUpdate.MAX_POSITION_SECONDS + 1, T0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ProgressUpdate(USER, LECTURE, 50, 40, T0)); // max < last
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ProgressUpdate.heartbeat(USER, LECTURE, 1, T0)
                    .merge(ProgressUpdate.heartbeat(USER, UUID.randomUUID(), 1, T0)));
  }

  @Test
  void timeIsKeptInMillisecondsLikeRedisKeepsIt() {
    ProgressUpdate u = ProgressUpdate.heartbeat(USER, LECTURE, 1, T0.plusNanos(1_234_567));
    assertThat(u.at()).isEqualTo(T0.plusMillis(1));
  }

  /** A player's life: forward, seeks back, pauses, two tabs — times may repeat, positions jump. */
  private static List<ProgressUpdate> heartbeats(Random random) {
    List<ProgressUpdate> beats = new ArrayList<>();
    int n = 1 + random.nextInt(30);
    for (int i = 0; i < n; i++) {
      int position = random.nextInt(3_600);
      Instant at = T0.plusSeconds(random.nextInt(20) * 15L); // collisions on purpose
      beats.add(
          random.nextInt(5) == 0
              ? new ProgressUpdate(USER, LECTURE, position, position + random.nextInt(600), at)
              : ProgressUpdate.heartbeat(USER, LECTURE, position, at));
    }
    return beats;
  }

  private static ProgressUpdate foldLeft(List<ProgressUpdate> updates) {
    return updates.stream().reduce(ProgressUpdate::merge).orElseThrow();
  }
}
