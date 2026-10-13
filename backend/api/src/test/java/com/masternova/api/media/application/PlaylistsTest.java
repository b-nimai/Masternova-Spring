package com.masternova.api.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaylistsTest {

  @Test
  void theMastersRungUrisCarryTheTicket() {
    String master =
        """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=385000,RESOLUTION=426x240
        240p/index.m3u8
        """;

    assertThat(Playlists.rewrite(master, uri -> uri + "?t=T"))
        .isEqualTo(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=385000,RESOLUTION=426x240
            240p/index.m3u8?t=T
            """);
  }

  @Test
  void aRungsInitSegmentAndSegmentsAreRewrittenTagsKept() {
    String rung =
        """
        #EXTM3U
        #EXT-X-MAP:URI="init.mp4"
        #EXTINF:6.000000,
        seg_00000.m4s

        #EXT-X-ENDLIST
        """;

    String out = Playlists.rewrite(rung, f -> "https://s3/" + f);

    assertThat(out)
        .contains("#EXT-X-MAP:URI=\"https://s3/init.mp4\"")
        .contains("#EXTINF:6.000000,\nhttps://s3/seg_00000.m4s\n")
        .endsWith("#EXT-X-ENDLIST\n");
  }
}
