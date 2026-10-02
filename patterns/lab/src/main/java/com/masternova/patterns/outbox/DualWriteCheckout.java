package com.masternova.patterns.outbox;

/**
 * ❌ THE BUG the outbox exists to fix: two writes to two systems, no transaction across them.
 * Commit the order, THEN publish. If the process dies (or the broker is down) between the two,
 * the order is paid and nobody ever hears about it: no receipt, no course access.
 */
public final class DualWriteCheckout {

  private final InMemoryDatabase db;
  private final Broker broker;

  public DualWriteCheckout(InMemoryDatabase db, Broker broker) {
    this.db = db;
    this.broker = broker;
  }

  /** @param crashBeforePublish simulates the process dying right after the DB commit */
  public void pay(String orderId, boolean crashBeforePublish) {
    db.inTransaction(tx -> tx.markOrderPaid(orderId)); //       write 1: committed
    if (crashBeforePublish) {
      throw new IllegalStateException("process killed"); //      … gone before write 2
    }
    broker.publish("OrderPaid:" + orderId); //                   write 2: may never happen
  }
}
