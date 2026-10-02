package com.masternova.java.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Test helper: run the same action on many PLATFORM threads, all released at the same instant. */
final class Race {

  private Race() {}

  static void run(int threads, Runnable action) throws InterruptedException {
    CountDownLatch startGate = new CountDownLatch(1); // ⭐ everyone waits here …
    List<Thread> started = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      started.add(
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      startGate.await();
                      action.run();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  }));
    }
    startGate.countDown(); //                          … and is released together: max contention
    for (Thread t : started) {
      t.join();
    }
  }
}
