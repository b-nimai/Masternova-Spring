package com.masternova.worker.pipeline.ffmpeg;

/** What ffprobe found in a source file — the input every later decision is made from. */
public record SourceInfo(double durationSeconds, int width, int height, boolean hasAudio) {

  public SourceInfo {
    if (durationSeconds <= 0 || width <= 0 || height <= 0) {
      throw new IllegalArgumentException("not a playable video: " + this);
    }
  }
}
