package com.masternova.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HlsPlaylistsTest {

  static final String RUNG =
      """
      #EXTM3U
      #EXT-X-MAP:URI="init.mp4"
      #EXTINF:6.000000,
      seg_00000.m4s

      #EXTINF:1.000000,
      seg_00001.m4s
      #EXT-X-ENDLIST
      """;

  @Test
  void theUrisAreTheInitSegmentAndEverySegment() {
    assertThat(HlsPlaylists.uris(RUNG))
        .containsExactly("init.mp4", "seg_00000.m4s", "seg_00001.m4s");
  }

  @Test
  void rewritingMapsOnlyUrisAndKeepsTags() {
    String out = HlsPlaylists.rewriteUris(RUNG, u -> "https://s3/" + u);

    assertThat(out)
        .contains("#EXT-X-MAP:URI=\"https://s3/init.mp4\"")
        .contains("#EXTINF:6.000000,\nhttps://s3/seg_00000.m4s\n")
        .endsWith("#EXT-X-ENDLIST\n");
  }

  @Test
  void aMasterPlaylistsRungUrisCanCarryATicket() {
    String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n240p/index.m3u8\n";
    assertThat(HlsPlaylists.rewriteUris(master, u -> u + "?t=T")).contains("240p/index.m3u8?t=T\n");
  }
}
