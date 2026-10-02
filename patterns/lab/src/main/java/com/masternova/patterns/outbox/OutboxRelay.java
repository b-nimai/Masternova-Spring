package com.masternova.patterns.outbox;

/**
 * ✅ <b>Transactional Outbox — Relay</b>. Reads unsent rows and publishes them; marks a row sent
 * only AFTER the broker accepted it. If the broker is down, rows stay unsent and the next run
 * retries — at-least-once delivery, so consumers must tolerate duplicates.
 */
public final class OutboxRelay {

  private final InMemoryDatabase db;
  private final Broker broker;

  public OutboxRelay(InMemoryDatabase db, Broker broker) {
    this.db = db;
    this.broker = broker;
  }

  /** @return how many rows were delivered this run */
  public int relayOnce() {
    int delivered = 0;
    for (InMemoryDatabase.OutboxRow row : db.unsentOutboxRows()) {
      try {
        broker.publish(row.event());
        db.markSent(row.id()); // ⭐ only after success — a crash here means a re-send, never a loss
        delivered++;
      } catch (IllegalStateException brokerDown) {
        break; // try again next run
      }
    }
    return delivered;
  }
}
