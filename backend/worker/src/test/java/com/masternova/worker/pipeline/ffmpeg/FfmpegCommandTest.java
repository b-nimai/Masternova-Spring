package com.masternova.worker.pipeline.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FfmpegCommandTest {

  static final URI SOURCE = URI.create("http://storage/media/a/source?X-Amz-Signature=abc&x=1");
  static final Path DIR = Path.of("/tmp/pipeline/job-1/720p");

  @Test
  void anHlsRenditionIsOneArgumentListInTheOrderFfmpegNeeds() {
    List<String> args =
        FfmpegCommand.hls("ffmpeg", SOURCE, DIR).rung(Rung.of(720, 2800, 128), "veryfast").build();

    assertThat(args.getFirst()).isEqualTo("ffmpeg");
    // ⭐ the url is ONE argument: its & and ? are never seen by a shell
    assertThat(args).containsSubsequence("-i", SOURCE.toString());
    assertThat(args.indexOf("-i")).isLessThan(args.indexOf("-map")); // input before output options
    assertThat(args).containsSubsequence("-vf", "scale=-2:720");
    assertThat(args).containsSubsequence("-b:v", "2800k", "-maxrate", "2996k", "-bufsize", "4200k");
    assertThat(args).containsSubsequence("-force_key_frames", "expr:gte(t,n_forced*2.0)");
    assertThat(args).containsSubsequence("-hls_time", "6", "-hls_segment_type", "fmp4");
    assertThat(args).containsSubsequence("-hls_segment_filename", DIR + "/seg_%05d.m4s");
    assertThat(args.getLast()).isEqualTo(DIR + "/index.m3u8"); // the output comes last
  }

  @Test
  void aSilentSourceMapsNoAudio() {
    List<String> args =
        FfmpegCommand.hls("ffmpeg", SOURCE, DIR)
            .rung(Rung.of(360, 800, 96), "veryfast")
            .withAudio(false)
            .withProgress(false)
            .build();

    assertThat(args).doesNotContain("0:a:0", "-c:a", "-progress");
  }

  @Test
  void aSegmentMustBeAWholeNumberOfGops() {
    var command =
        FfmpegCommand.hls("ffmpeg", SOURCE, DIR)
            .rung(Rung.of(360, 800, 96), "veryfast")
            .segments(Duration.ofSeconds(6), Duration.ofSeconds(4));

    assertThatThrownBy(command::build).hasMessageContaining("whole number of GOPs");
  }

  @Test
  void aRenditionNeedsARung() {
    assertThatThrownBy(() -> FfmpegCommand.hls("ffmpeg", SOURCE, DIR).build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void bandwidthIsThePeakOfVideoPlusAudio() {
    assertThat(Rung.of(720, 2800, 128).bandwidth()).isEqualTo((2996 + 128) * 1000);
    assertThatThrownBy(() -> Rung.of(721, 1, 1)).hasMessageContaining("even height");
  }

  @Test
  void stillImagesAreConvertedToFullRangeForTheJpegEncoder() { // found by the e2e upload test
    assertThat(StillCommands.poster("ffmpeg", SOURCE, 1, DIR.resolve("p.jpg")))
        .anyMatch(a -> a.endsWith(":out_range=full,format=yuvj420p"));
    assertThat(StillCommands.sprite("ffmpeg", SOURCE, 10, 2, DIR.resolve("s.jpg")))
        .anyMatch(a -> a.contains(":out_range=full,format=yuvj420p,tile=10x2"));
  }
}
