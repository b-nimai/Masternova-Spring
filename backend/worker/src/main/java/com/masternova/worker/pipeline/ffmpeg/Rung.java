package com.masternova.worker.pipeline.ffmpeg;

/**
 * One HLS rendition: its height (width follows the aspect ratio) and target bitrates.
 *
 * @param name the rung's key in storage ({@code MediaKeys.rung}): {@code 720p}
 */
public record Rung(String name, int height, int videoKbps, int audioKbps) {

  public Rung {
    if (height < 2 || height % 2 != 0) {
      throw new IllegalArgumentException("H.264 4:2:0 needs an even height: " + height);
    }
  }

  public static Rung of(int height, int videoKbps, int audioKbps) {
    return new Rung(height + "p", height, videoKbps, audioKbps);
  }

  /** Peak video bitrate handed to x264 ({@code -maxrate}): 7 % over the target. */
  public int maxrateKbps() {
    return videoKbps * 107 / 100;
  }

  /** The master playlist's {@code BANDWIDTH}: the PEAK bits/s of video + audio. */
  public int bandwidth() {
    return (maxrateKbps() + audioKbps) * 1000;
  }
}
