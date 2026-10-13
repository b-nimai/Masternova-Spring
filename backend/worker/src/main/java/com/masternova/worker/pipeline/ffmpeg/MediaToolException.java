package com.masternova.worker.pipeline.ffmpeg;

/**
 * ffmpeg / ffprobe failed. {@link #permanent()} = the INPUT is the problem (corrupt, not a video):
 * retrying can't help, so the job is buried at once instead of retried five times.
 */
public class MediaToolException extends RuntimeException {

  private final boolean permanent;

  public MediaToolException(String message, boolean permanent) {
    super(message);
    this.permanent = permanent;
  }

  public boolean permanent() {
    return permanent;
  }
}
