package com.masternova.java.concurrency;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

/**
 * Producer–consumer with a {@link BlockingQueue}: one producer reads outbox events, N workers
 * deliver them. The bounded queue gives BACK-PRESSURE — if workers fall behind, put() blocks the
 * producer instead of buffering unboundedly. A "poison pill" per worker shuts them down cleanly.
 *
 * <p>A miniature of the worker's outbox relay (Phases 2 and 4).
 */
public final class OutboxRelay {

  private static final String POISON_PILL = "__STOP__";

  private OutboxRelay() {}

  /** Delivers every event using {@code workers} consumer threads; returns what was delivered. */
  public static List<String> relay(List<String> events, int workers) throws InterruptedException {
    BlockingQueue<String> queue = new ArrayBlockingQueue<>(8); // ⭐ bounded on purpose
    ConcurrentLinkedQueue<String> delivered = new ConcurrentLinkedQueue<>();

    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int w = 0; w < workers; w++) {
        pool.submit(
            () -> {
              while (true) {
                String event = queue.take(); // ⭐ blocks while empty
                if (event.equals(POISON_PILL)) {
                  return null; //                  clean shutdown
                }
                delivered.add(event + " ✓");
              }
            });
      }
      for (String event : events) {
        queue.put(event); // ⭐ blocks while full: back-pressure
      }
      for (int w = 0; w < workers; w++) {
        queue.put(POISON_PILL); // one pill per worker
      }
    } // waits for the workers to drain the queue and stop
    return List.copyOf(delivered);
  }
}
