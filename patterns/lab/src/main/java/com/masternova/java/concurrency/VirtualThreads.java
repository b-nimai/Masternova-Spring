package com.masternova.java.concurrency;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Virtual threads (Java 21): cheap threads scheduled by the JVM onto a few OS ("carrier")
 * threads. A virtual thread that BLOCKS (sleep, socket read, JDBC) is unmounted and its carrier
 * runs another one — so 10,000 concurrent blocking tasks need ~10,000 cheap objects, not 10,000
 * OS threads.
 */
public final class VirtualThreads {

  private VirtualThreads() {}

  /** What a run looked like — for the test to assert on. */
  public record Run(int completed, int distinctCarriers, Duration elapsed) {}

  /** Starts {@code tasks} tasks that each block for {@code blockFor}, all at once. */
  public static Run blockingTasks(int tasks, Duration blockFor) {
    AtomicInteger completed = new AtomicInteger();
    Set<String> carriers = ConcurrentHashMap.newKeySet();
    long start = System.nanoTime();

    // ⭐ One new virtual thread PER TASK — no pool to size. ExecutorService is AutoCloseable
    //    (Java 19+): close() waits for all submitted tasks to finish.
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      IntStream.range(0, tasks)
          .forEach(
              i ->
                  executor.submit(
                      () -> {
                        carriers.add(carrierName());
                        Thread.sleep(blockFor); // blocking is FINE on a virtual thread
                        completed.incrementAndGet();
                        return null; // a Callable, so sleep's InterruptedException is allowed
                      }));
    } // ← waits here

    return new Run(completed.get(), carriers.size(), Duration.ofNanos(System.nanoTime() - start));
  }

  private static String carrierName() {
    // toString of a virtual thread: "VirtualThread[#42]/runnable@ForkJoinPool-1-worker-3"
    String description = Thread.currentThread().toString();
    int at = description.indexOf('@');
    return at < 0 ? description : description.substring(at + 1);
  }
}
