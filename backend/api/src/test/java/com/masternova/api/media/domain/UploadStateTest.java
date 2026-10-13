package com.masternova.api.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.media.domain.UploadState.Step;
import com.masternova.api.platform.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ⭐ The LLD's event × state table (media.md §3), cell by cell. */
class UploadStateTest {

  @ParameterizedTest(name = "{1} on {0} → {2}")
  @CsvSource({
    "OPEN,      resume,   PERFORM",
    "OPEN,      complete, PERFORM",
    "OPEN,      abort,    PERFORM",
    "OPEN,      expire,   PERFORM",
    "COMPLETED, resume,   UPLOAD_ALREADY_COMPLETED",
    "COMPLETED, complete, REPLAY",
    "COMPLETED, abort,    UPLOAD_ALREADY_COMPLETED",
    "COMPLETED, expire,   REPLAY",
    "ABORTED,   resume,   UPLOAD_ABORTED",
    "ABORTED,   complete, UPLOAD_ABORTED",
    "ABORTED,   abort,    REPLAY",
    "ABORTED,   expire,   REPLAY",
    "EXPIRED,   resume,   UPLOAD_EXPIRED",
    "EXPIRED,   complete, UPLOAD_EXPIRED",
    "EXPIRED,   abort,    REPLAY",
    "EXPIRED,   expire,   REPLAY"
  })
  void everyCell(UploadStatus status, String event, String expected) {
    UploadState state = UploadState.of(status);
    Function<UploadState, Step> fire =
        switch (event) {
          case "resume" -> UploadState::resume;
          case "complete" -> UploadState::complete;
          case "abort" -> UploadState::abort;
          case "expire" -> UploadState::expire;
          default -> throw new IllegalArgumentException(event);
        };

    if (expected.equals("PERFORM") || expected.equals("REPLAY")) {
      assertThat(fire.apply(state)).isEqualTo(Step.valueOf(expected));
    } else {
      assertThatThrownBy(() -> fire.apply(state))
          .isInstanceOfSatisfying(
              ConflictException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }
  }

  @Test
  void anOpenSessionPastItsExpiryIsAlreadyExpired() {
    Instant now = Instant.parse("2026-10-13T10:00:00Z");
    UploadSession session =
        UploadSession.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "upload-1",
            "media/x/source",
            PartPlan.forSize(1000),
            now,
            now.plus(Duration.ofHours(24)));

    assertThat(session.state(now.plus(Duration.ofHours(23))).status()).isEqualTo(UploadStatus.OPEN);
    // ⭐ before the sweeper has even run, the user can no longer complete it
    assertThat(session.state(now.plus(Duration.ofHours(24))).status())
        .isEqualTo(UploadStatus.EXPIRED);
    assertThat(session.status()).isEqualTo(UploadStatus.OPEN); // the row changes when swept
  }

  @Test
  void transitionsRefuseToRunTwice() {
    Instant now = Instant.now();
    UploadSession session =
        UploadSession.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "u",
            "k",
            PartPlan.forSize(10),
            now,
            now.plusSeconds(60));
    session.markCompleted(now);
    assertThatThrownBy(() -> session.markCompleted(now)).isInstanceOf(IllegalStateException.class);
  }
}
