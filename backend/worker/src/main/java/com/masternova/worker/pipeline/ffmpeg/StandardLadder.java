package com.masternova.worker.pipeline.ffmpeg;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The production ladder (ADR-0013): 360p / 720p / 1080p, H.264 High + AAC-LC, {@code veryfast}. */
@Component
@ConditionalOnProperty(
    name = "masternova.pipeline.ladder",
    havingValue = "standard",
    matchIfMissing = true)
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "ConcreteStrategy",
    note = "patterns/docs/01-strategy.md")
class StandardLadder implements LadderProfile {

  private static final List<Rung> RUNGS =
      List.of(Rung.of(360, 800, 96), Rung.of(720, 2800, 128), Rung.of(1080, 5000, 160));

  @Override
  public List<Rung> rungs() {
    return RUNGS;
  }

  @Override
  public String preset() {
    return "veryfast";
  }
}
