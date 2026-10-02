package com.masternova.java.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ContextQueueAndVisibilityTest {

  @Test
  void scopedValueIsBoundOnlyInsideTheScope() throws Exception {
    String inside = RequestContext.runAs("asha", RequestContext::currentUser);

    assertThat(inside).isEqualTo("asha");
    assertThat(RequestContext.currentUser()).isEqualTo("anonymous"); // automatically unbound
  }

  @Test
  void scopesNestAndRestore() throws Exception {
    String result =
        RequestContext.runAs("admin", () -> {
          String impersonated = RequestContext.runAs("asha", RequestContext::currentUser);
          return impersonated + " then " + RequestContext.currentUser();
        });

    assertThat(result).isEqualTo("asha then admin");
  }

  @Test
  void everyEventIsDeliveredExactlyOnceByTheWorkerPool() throws InterruptedException {
    List<String> events = IntStream.range(0, 100).mapToObj(i -> "evt_" + i).toList();

    List<String> delivered = OutboxRelay.relay(events, 4);

    assertThat(delivered).hasSize(100).doesNotHaveDuplicates().contains("evt_0 ✓", "evt_99 ✓");
  }

  @Test
  void aVolatileFlagIsSeenByTheSpinningThread() throws InterruptedException {
    StopFlag flag = new StopFlag();
    Thread worker = Thread.ofPlatform().start(flag::runUntilStopped);

    Thread.sleep(50);
    flag.stop();
    worker.join(5_000);

    assertThat(worker.isAlive()).as("the worker must observe running=false and exit").isFalse();
  }
}
