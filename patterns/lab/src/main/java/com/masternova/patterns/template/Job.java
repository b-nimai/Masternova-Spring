package com.masternova.patterns.template;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A Template Method whose skeleton protects INVARIANTS, not just an order of steps (production:
 * {@code com.masternova.worker.pipeline.job.JobProcessor}):
 *
 * <ol>
 *   <li>a scratch area exists only for the run;
 *   <li>{@link #produce} — the one step subclasses write;
 *   <li>outputs are published with COMMIT MARKERS LAST (a reader never sees a playlist before its
 *       segments);
 *   <li>the scratch area is cleaned up in {@code finally} — even when {@link #produce} throws.
 * </ol>
 */
public abstract class Job {

  /** A file to publish; markers ({@code index.m3u8}) say "everything before me is complete". */
  public record Output(String name, boolean marker) {}

  public final List<String> published = new ArrayList<>();
  public boolean scratchExists;

  /** ⭐ final: subclasses can't reorder the uploads or skip the cleanup. */
  public final void run() {
    scratchExists = true;
    try {
      produce().stream()
          .sorted(Comparator.comparing(Output::marker)) // false < true: markers last
          .forEach(o -> published.add(o.name()));
    } finally {
      scratchExists = false;
    }
  }

  protected abstract List<Output> produce();
}
