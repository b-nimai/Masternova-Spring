package com.masternova.worker.pipeline.ffmpeg;

import com.masternova.storage.MediaKeys;
import com.masternova.worker.pipeline.PipelineProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ffprobe over a presigned url: it reads the container header (a few range requests), not the whole
 * file. The source's duration, size and audio decide the ladder (video-pipeline LLD §3).
 */
@Component
public class Ffprobe {

  private final ProcessRunner runner;
  private final JsonMapper json;
  private final String ffprobe;

  Ffprobe(ProcessRunner runner, JsonMapper json, PipelineProperties props) {
    this.runner = runner;
    this.json = json;
    this.ffprobe = props.ffprobePath();
  }

  public SourceInfo probe(URI source, Path workDir) {
    StringBuilder out = new StringBuilder();
    runner.run(
        List.of(
            ffprobe,
            "-v",
            "error",
            "-print_format",
            "json",
            "-show_format",
            "-show_streams",
            source.toString()),
        workDir,
        Duration.ofMinutes(2),
        line -> out.append(line).append('\n'));
    return parse(out.toString());
  }

  /** Package-visible for the parser test: real ffprobe JSON, no process. */
  SourceInfo parse(String ffprobeJson) {
    JsonNode root = json.readTree(ffprobeJson);
    JsonNode video = null;
    boolean audio = false;
    for (JsonNode stream : root.path("streams")) {
      String type = stream.path("codec_type").asString("");
      if (type.equals("video") && video == null && !isCoverArt(stream)) {
        video = stream;
      }
      audio |= type.equals("audio");
    }
    if (video == null) {
      throw new MediaToolException("the file has no video stream", true); // ⭐ permanent
    }
    int width = video.path("width").asInt(0);
    int height = video.path("height").asInt(0);
    if (width < MediaKeys.MIN_RUNG_HEIGHT || height < MediaKeys.MIN_RUNG_HEIGHT) {
      // ⭐ permanent: nothing playable to make of it (found in review: height 0 became a '2p' rung
      //    that failed only after five full encodes)
      throw new MediaToolException("the video is too small: " + width + "x" + height, true);
    }
    double duration = root.path("format").path("duration").asDouble(0);
    if (duration <= 0) {
      throw new MediaToolException("the file has no duration", true);
    }
    return new SourceInfo(duration, width, height, audio);
  }

  /** An MP3/M4A's album art is a one-frame "video" stream; it isn't the video we want. */
  private static boolean isCoverArt(JsonNode stream) {
    return stream.path("disposition").path("attached_pic").asInt(0) == 1;
  }
}
