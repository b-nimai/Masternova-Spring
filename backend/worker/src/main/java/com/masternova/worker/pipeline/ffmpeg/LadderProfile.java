package com.masternova.worker.pipeline.ffmpeg;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;

/**
 * STRATEGY: which renditions to make of a source, and how hard x264 works on them. Two real
 * strategies: {@link StandardLadder} (quality, prod) and {@link FastLadder} (speed: dev, CI, the
 * crash IT). Picked by {@code masternova.pipeline.ladder}; the pipeline's code path is the same.
 *
 * <p>Shared rule (the default method): never upscale — rungs above the source's height are dropped
 * — and always keep one rung (a tiny source gets one at its own height).
 */
@DesignPattern(value = Pattern.STRATEGY, role = "Strategy", note = "patterns/docs/01-strategy.md")
public interface LadderProfile {

  /** Every rung this profile can make, lowest first. */
  List<Rung> rungs();

  /** x264's {@code -preset}. */
  String preset();

  /** The rungs for THIS source. */
  default List<Rung> rungsFor(SourceInfo source) {
    List<Rung> fitting = rungs().stream().filter(r -> r.height() <= source.height()).toList();
    if (!fitting.isEmpty()) {
      return fitting;
    }
    Rung lowest = rungs().getFirst();
    int even = source.height() - source.height() % 2;
    return List.of(Rung.of(Math.max(2, even), lowest.videoKbps(), lowest.audioKbps()));
  }
}
