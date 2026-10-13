package com.masternova.patterns.strategy.ladder;

import java.util.List;

/**
 * STRATEGY with a shared rule in a default method (production: {@code
 * com.masternova.worker.pipeline.ffmpeg.LadderProfile}). Each strategy only says WHICH heights it
 * would like; the interface applies "never upscale, keep at least one" for all of them.
 */
public interface Ladder {

  List<Integer> heights();

  default List<Integer> heightsFor(int sourceHeight) {
    List<Integer> fit = heights().stream().filter(h -> h <= sourceHeight).toList();
    return fit.isEmpty() ? List.of(sourceHeight - sourceHeight % 2) : fit;
  }

  /** Production quality. */
  record Standard() implements Ladder {
    @Override
    public List<Integer> heights() {
      return List.of(360, 720, 1080);
    }
  }

  /** CI speed: same code path, cheaper output. */
  record Fast() implements Ladder {
    @Override
    public List<Integer> heights() {
      return List.of(240, 360);
    }
  }

  /** Picked by configuration, not by an {@code if (ci)} in the caller. */
  static Ladder named(String name) {
    return switch (name) {
      case "standard" -> new Standard();
      case "fast" -> new Fast();
      default -> throw new IllegalArgumentException("no ladder " + name);
    };
  }
}
