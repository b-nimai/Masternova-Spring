package com.masternova.worker.pipeline.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.masternova.worker.pipeline.PipelineProperties;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FfprobeTest {

  static PipelineProperties props() {
    return new PipelineProperties(
        true,
        "fast",
        "ffmpeg",
        "ffprobe",
        Path.of("/tmp/pipeline"),
        1,
        Duration.ofSeconds(1),
        Duration.ofMinutes(2),
        Duration.ofSeconds(30),
        5,
        Duration.ofSeconds(30),
        Duration.ofHours(6));
  }

  private final Ffprobe ffprobe = new Ffprobe(mock(ProcessRunner.class), new JsonMapper(), props());

  @Test
  void readsDurationSizeAndAudio() {
    SourceInfo info =
        ffprobe.parse(
            """
            {"streams": [
               {"codec_type": "video", "width": 1920, "height": 1080},
               {"codec_type": "audio"}],
             "format": {"duration": "612.480000"}}
            """);
    assertThat(info).isEqualTo(new SourceInfo(612.48, 1920, 1080, true));
  }

  @Test
  void albumArtIsNotAVideo() {
    assertThatThrownBy(
            () ->
                ffprobe.parse(
                    """
                    {"streams": [
                       {"codec_type": "audio"},
                       {"codec_type": "video", "width": 500, "height": 500,
                        "disposition": {"attached_pic": 1}}],
                     "format": {"duration": "180.0"}}
                    """))
        .isInstanceOfSatisfying(MediaToolException.class, e -> assertThat(e.permanent()).isTrue());
  }

  @Test
  void aSilentVideoHasNoAudio() {
    SourceInfo info =
        ffprobe.parse(
            """
            {"streams": [{"codec_type": "video", "width": 640, "height": 360}],
             "format": {"duration": "10"}}
            """);
    assertThat(info.hasAudio()).isFalse();
  }
}
