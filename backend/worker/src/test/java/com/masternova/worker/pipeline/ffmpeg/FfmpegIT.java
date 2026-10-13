package com.masternova.worker.pipeline.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.worker.pipeline.TestClips;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * The builders against the REAL ffmpeg/ffprobe (installed on dev machines and in CI): the command
 * the code builds actually produces the HLS layout {@code MediaKeys} names, and a rerun produces
 * the same file set (the basis of 7.10's "overwrite, don't duplicate").
 */
class FfmpegIT {

  @TempDir Path dir;
  private final ProcessRunner runner = new ProcessRunner();
  private final Ffprobe ffprobe = new Ffprobe(runner, new JsonMapper(), FfprobeTest.props());

  @Test
  void probeThenTranscodeOneRungIntoHls() throws Exception {
    Path clip = TestClips.mp4(dir, 13, 1280, 720, true);

    SourceInfo info = ffprobe.probe(clip.toUri(), dir);
    assertThat(info.height()).isEqualTo(720);
    assertThat(info.hasAudio()).isTrue();
    assertThat(info.durationSeconds()).isBetween(12.9, 13.2);

    Path out = Files.createDirectories(dir.resolve("360p"));
    List<Integer> progress = new ArrayList<>();
    ProgressParser parser = new ProgressParser(info.durationSeconds());
    runner.run(
        FfmpegCommand.hls("ffmpeg", clip.toUri(), out)
            .rung(Rung.of(360, 600, 64), "ultrafast")
            .build(),
        dir,
        Duration.ofMinutes(1),
        line -> parser.percent(line).ifPresent(progress::add));

    assertThat(names(out))
        .containsExactly(
            "index.m3u8", "init.mp4", "seg_00000.m4s", "seg_00001.m4s", "seg_00002.m4s");
    String playlist = Files.readString(out.resolve("index.m3u8"));
    assertThat(playlist).contains("#EXT-X-PLAYLIST-TYPE:VOD", "#EXT-X-MAP:URI=\"init.mp4\"");
    assertThat(playlist).contains("#EXTINF:6.000000,").contains("#EXT-X-ENDLIST");
    assertThat(progress).last().isEqualTo(100);

    // ⭐ a rerun into the same directory writes the SAME names — nothing extra to clean up
    List<String> first = names(out);
    runner.run(
        FfmpegCommand.hls("ffmpeg", clip.toUri(), out)
            .rung(Rung.of(360, 600, 64), "ultrafast")
            .build(),
        dir,
        Duration.ofMinutes(1),
        line -> {});
    assertThat(names(out)).isEqualTo(first);
  }

  @Test
  void posterAndSprite() throws Exception {
    Path clip = TestClips.mp4(dir, 25, 640, 360, false);

    runner.run(
        StillCommands.poster("ffmpeg", clip.toUri(), 2.5, dir.resolve("poster.jpg")),
        dir,
        Duration.ofMinutes(1),
        l -> {});
    runner.run(
        StillCommands.sprite(
            "ffmpeg", clip.toUri(), StillCommands.spriteInterval(25), 1, dir.resolve("sprite.jpg")),
        dir,
        Duration.ofMinutes(1),
        l -> {});

    assertThat(dir.resolve("poster.jpg")).isNotEmptyFile();
    assertThat(dir.resolve("sprite.jpg")).isNotEmptyFile();
  }

  @Test
  void aFileThatIsNotAVideoIsAPermanentFailure() throws Exception {
    Path junk = Files.writeString(dir.resolve("notes.mp4"), "this is not a video");

    assertThatThrownBy(() -> ffprobe.probe(junk.toUri(), dir))
        .isInstanceOfSatisfying(MediaToolException.class, e -> assertThat(e.permanent()).isTrue());
  }

  private static List<String> names(Path dir) throws Exception {
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(p -> p.getFileName().toString()).sorted().toList();
    }
  }
}
