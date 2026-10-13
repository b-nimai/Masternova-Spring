package com.masternova.worker.pipeline.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.worker.pipeline.PipelineStore.Rendition;
import com.masternova.worker.pipeline.ffmpeg.SourceInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The small text formats the pipeline writes and reads: master playlist, rung playlist, VTT. */
class PlaylistsTest {

  @Test
  void theMasterPlaylistListsRungsLowestFirstWithBandwidthResolutionAndCodecs() {
    String master =
        PackageProcessor.masterPlaylist(
            List.of(
                new Rendition("240p", 426, 240, 385000, "k/240p/index.m3u8", 3),
                new Rendition("360p", 640, 360, 706000, "k/360p/index.m3u8", 3)),
            true);

    assertThat(master)
        .startsWith("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-INDEPENDENT-SEGMENTS\n")
        .contains(
            "#EXT-X-STREAM-INF:BANDWIDTH=385000,RESOLUTION=426x240,CODECS=\"avc1.640028,mp4a.40.2\"\n240p/index.m3u8\n")
        .endsWith("360p/index.m3u8\n");
    assertThat(
            PackageProcessor.masterPlaylist(
                List.of(new Rendition("240p", 426, 240, 1, "k", 1)), false))
        .contains("CODECS=\"avc1.640028\"");
  }

  @Test
  void spriteCuesTileLeftToRightThenDown() {
    SourceInfo source = new SourceInfo(125, 1280, 720, true);

    String vtt = ThumbnailProcessor.spriteVtt(source, 10, 13);

    assertThat(vtt)
        .startsWith("WEBVTT\n\n00:00:00.000 --> 00:00:10.000\nsprite.jpg#xywh=0,0,160,90\n");
    assertThat(vtt)
        .contains("00:01:40.000 --> 00:01:50.000\nsprite.jpg#xywh=0,90,160,90"); // tile 11
    assertThat(vtt).contains("00:02:00.000 --> 00:02:05.000"); // the last cue ends at the duration
  }

  @Test
  void renditionWidthKeepsTheAspectRatioAndIsEven() {
    assertThat(TranscodeProcessor.evenWidth(new SourceInfo(1, 1920, 1080, true), 360))
        .isEqualTo(640);
    assertThat(TranscodeProcessor.evenWidth(new SourceInfo(1, 1280, 720, true), 240))
        .isEqualTo(426);
  }
}
