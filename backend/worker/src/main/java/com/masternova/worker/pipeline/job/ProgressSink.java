package com.masternova.worker.pipeline.job;

/** Where a running job reports how far it is (0..100). Lossy by design: only the latest counts. */
@FunctionalInterface
public interface ProgressSink {

  ProgressSink NONE = percent -> {};

  void report(int percent);
}
