package com.masternova.worker.pipeline.ffmpeg;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev and CI: two small rungs at {@code ultrafast}, so a test clip transcodes in about a second and
 * still goes through every step of the real pipeline.
 */
@Component
@ConditionalOnProperty(name = "masternova.pipeline.ladder", havingValue = "fast")
@DesignPattern(
    value = Pattern.STRATEGY,
    role = "ConcreteStrategy",
    note = "patterns/docs/01-strategy.md")
class FastLadder implements LadderProfile {

  private static final List<Rung> RUNGS = List.of(Rung.of(240, 300, 64), Rung.of(360, 600, 64));

  @Override
  public List<Rung> rungs() {
    return RUNGS;
  }

  @Override
  public String preset() {
    return "ultrafast";
  }
}
