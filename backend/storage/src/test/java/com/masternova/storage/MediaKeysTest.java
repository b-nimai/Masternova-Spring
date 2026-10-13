package com.masternova.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaKeysTest {

  private static final UUID ASSET = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @Test
  void keysAreAFunctionOfIdsSoARerunWritesTheSameKeys() {
    assertThat(MediaKeys.source(ASSET)).isEqualTo("media/" + ASSET + "/source");
    assertThat(MediaKeys.master(ASSET)).isEqualTo("media/" + ASSET + "/hls/master.m3u8");
    assertThat(MediaKeys.rungPlaylist(ASSET, "720p"))
        .isEqualTo("media/" + ASSET + "/hls/720p/index.m3u8");
    assertThat(MediaKeys.segment(ASSET, "720p", 7))
        .isEqualTo("media/" + ASSET + "/hls/720p/seg_00007.m4s")
        .isEqualTo(MediaKeys.segment(ASSET, "720p", 7)); // ⭐ deterministic
  }

  @Test
  void everyAssetKeyIsUnderTheAssetPrefix() {
    assertThat(
            java.util.List.of(
                MediaKeys.source(ASSET),
                MediaKeys.master(ASSET),
                MediaKeys.segment(ASSET, "360p", 0),
                MediaKeys.poster(ASSET),
                MediaKeys.sprite(ASSET),
                MediaKeys.spriteVtt(ASSET)))
        .allMatch(k -> k.startsWith(MediaKeys.asset(ASSET)));
  }

  @Test
  void aRungNameCannotEscapeItsPrefix() {
    assertThatThrownBy(() -> MediaKeys.rung(ASSET, "../source"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
