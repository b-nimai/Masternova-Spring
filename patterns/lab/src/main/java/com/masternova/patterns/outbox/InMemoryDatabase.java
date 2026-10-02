package com.masternova.patterns.outbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A toy database with ALL-OR-NOTHING transactions: writes are staged and only applied if the
 * transaction function finishes without throwing. Just enough to show why the outbox works.
 */
public final class InMemoryDatabase {

  /** An outbox row: an event waiting to be delivered. */
  public record OutboxRow(long id, String event, boolean sent) {}

  /** What a transaction may write. */
  public interface Tx {
    void markOrderPaid(String orderId);

    void appendToOutbox(String event);
  }

  private final List<String> paidOrders = new ArrayList<>();
  private final Map<Long, OutboxRow> outbox = new LinkedHashMap<>();
  private long nextOutboxId = 1;

  /** Runs {@code work} as one transaction: commit if it returns, roll back if it throws. */
  public synchronized void inTransaction(Consumer<Tx> work) {
    List<String> stagedOrders = new ArrayList<>();
    List<String> stagedEvents = new ArrayList<>();
    work.accept(
        new Tx() {
          @Override
          public void markOrderPaid(String orderId) {
            stagedOrders.add(orderId);
          }

          @Override
          public void appendToOutbox(String event) {
            stagedEvents.add(event);
          }
        });
    // ⭐ commit: only reached if work didn't throw — both writes land together, or neither does
    paidOrders.addAll(stagedOrders);
    stagedEvents.forEach(e -> outbox.put(nextOutboxId, new OutboxRow(nextOutboxId++, e, false)));
  }

  public synchronized List<String> paidOrders() {
    return List.copyOf(paidOrders);
  }

  public synchronized List<OutboxRow> unsentOutboxRows() {
    return outbox.values().stream().filter(r -> !r.sent()).toList();
  }

  public synchronized void markSent(long outboxId) {
    outbox.computeIfPresent(outboxId, (id, row) -> new OutboxRow(id, row.event(), true));
  }
}
