package com.masternova.patterns.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TranscodeArgsTest {

  @Test
  void aUrlWithShellCharactersStaysOneArgument() {
    String url = "http://s3/media/a/source?X-Amz-Signature=a&b=c d";
    var args = TranscodeArgs.of(url, "out/index.m3u8").height(360).build();

    assertThat(args).containsSubsequence("-i", url);
    assertThat(args.getLast()).isEqualTo("out/index.m3u8");
  }

  @Test
  void defaultsLiveInOnePlace() {
    assertThat(TranscodeArgs.of("in", "out").build()).contains("scale=-2:720", "0:a:0");
    assertThat(TranscodeArgs.of("in", "out").withoutAudio().build()).doesNotContain("0:a:0");
  }

  @Test
  void crossFieldRulesAreCheckedOnceInBuild() {
    assertThatThrownBy(() -> TranscodeArgs.of("in", "out").segments(6, 4).build())
        .hasMessageContaining("whole number of GOPs");
  }
}
