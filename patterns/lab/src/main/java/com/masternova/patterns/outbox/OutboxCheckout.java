package com.masternova.patterns.outbox;

/**
 * ✅ <b>Transactional Outbox — Writer</b>. The event is written to an outbox TABLE in the SAME
 * transaction as the order. One database, one commit: both rows or neither. Delivery to the
 * broker happens later, in the {@link OutboxRelay}.
 */
public final class OutboxCheckout {

  private final InMemoryDatabase db;

  public OutboxCheckout(InMemoryDatabase db) {
    this.db = db;
  }

  public void pay(String orderId, boolean failValidationAfterWrites) {
    db.inTransaction(tx -> {
      tx.markOrderPaid(orderId);
      tx.appendToOutbox("OrderPaid:" + orderId); // ⭐ same transaction as the state change
      if (failValidationAfterWrites) {
        throw new IllegalStateException("rolled back"); // → neither row is committed
      }
    });
  }
}
