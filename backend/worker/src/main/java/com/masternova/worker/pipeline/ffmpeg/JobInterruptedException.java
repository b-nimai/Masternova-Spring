package com.masternova.worker.pipeline.ffmpeg;

/**
 * The job was interrupted because THIS process is shutting down (a deploy, a scale-in) — not
 * because anything is wrong with the media. The runner hands the job back unchanged instead of
 * counting a failed attempt (found in review: five rolling deploys buried a healthy job).
 */
public class JobInterruptedException extends RuntimeException {

  public JobInterruptedException(String message) {
    super(message);
  }
}
