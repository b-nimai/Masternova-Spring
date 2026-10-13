package com.masternova.worker.pipeline.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProgressParserTest {

  private final ProgressParser parser = new ProgressParser(200);

  @Test
  void outTimeBecomesAPercentageOfTheDuration() {
    assertThat(parser.percent("out_time_us=50000000")).hasValue(25);
    assertThat(parser.percent("out_time_us=199999999")).hasValue(99);
  }

  @Test
  void onlyTheEndMarkerIsOneHundred() {
    assertThat(parser.percent("out_time_us=250000000")).hasValue(99); // overshoot is clamped
    assertThat(parser.percent("progress=end")).hasValue(100);
  }

  @Test
  void otherLinesAndNotYetKnownSayNothing() {
    assertThat(parser.percent("frame=120")).isEmpty();
    assertThat(parser.percent("progress=continue")).isEmpty();
    assertThat(parser.percent("out_time_us=N/A")).isEmpty();
    assertThat(parser.percent("out_time_us=garbage")).isEmpty(); // never throws
  }
}
