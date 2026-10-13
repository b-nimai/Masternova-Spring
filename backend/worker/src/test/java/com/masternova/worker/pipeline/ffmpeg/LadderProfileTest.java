package com.masternova.worker.pipeline.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LadderProfileTest {

  private final LadderProfile standard = new StandardLadder();
  private final LadderProfile fast = new FastLadder();

  static SourceInfo source(int height) {
    return new SourceInfo(60, height * 16 / 9, height, true);
  }

  @Test
  void aFullHdSourceGetsTheWholeLadder() {
    assertThat(standard.rungsFor(source(1080)))
        .extracting(Rung::name)
        .containsExactly("360p", "720p", "1080p");
  }

  @Test
  void neverUpscale() {
    assertThat(standard.rungsFor(source(720)))
        .extracting(Rung::name)
        .containsExactly("360p", "720p");
    assertThat(standard.rungsFor(source(719))).extracting(Rung::name).containsExactly("360p");
  }

  @Test
  void aTinySourceStillGetsOneRungAtItsOwnEvenHeight() {
    assertThat(standard.rungsFor(source(201)))
        .singleElement()
        .satisfies(r -> assertThat(r.height()).isEqualTo(200));
  }

  @Test
  void theFastLadderIsSmallAndCheap() {
    assertThat(fast.rungsFor(source(1080))).extracting(Rung::name).containsExactly("240p", "360p");
    assertThat(fast.preset()).isEqualTo("ultrafast");
  }
}
