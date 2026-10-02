package com.masternova.java.concurrency;

/**
 * VISIBILITY: without {@code volatile}, the worker thread may never SEE the write made by
 * stop() — the JIT can hoist the read out of the loop or keep it in a CPU cache. {@code volatile}
 * guarantees every read sees the latest write (a "happens-before" edge).
 */
public final class StopFlag {

  private volatile boolean running = true; // ⭐ remove volatile and runUntilStopped() may spin forever
  private long iterations;

  /** Spins until another thread calls stop(); returns how many loops it did. */
  public long runUntilStopped() {
    while (running) {
      iterations++;
    }
    return iterations;
  }

  public void stop() {
    running = false;
  }
}
