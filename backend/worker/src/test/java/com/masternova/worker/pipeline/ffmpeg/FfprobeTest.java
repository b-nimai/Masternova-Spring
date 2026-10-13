package com.masternova.worker.pipeline.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.masternova.worker.pipeline.PipelineProperties;
import com.masternova.worker.pipeline.TestPipelineProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FfprobeTest {

  static PipelineProperties props() {
    return TestPipelineProperties.defaults();
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
  void aVideoWithNoUsableSizeFailsAtOnce() {
    assertThatThrownBy(
            () ->
                ffprobe.parse(
                    """
                    {"streams": [{"codec_type": "video", "width": 0, "height": 0}],
                     "format": {"duration": "10"}}
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
