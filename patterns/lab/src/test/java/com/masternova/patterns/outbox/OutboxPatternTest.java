package com.masternova.patterns.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxPatternTest {

  @Test
  void dualWriteLosesTheEventWhenTheProcessDiesBetweenTheWrites() {
    InMemoryDatabase db = new InMemoryDatabase();
    Broker broker = new Broker();

    assertThatThrownBy(() -> new DualWriteCheckout(db, broker).pay("o1", true))
        .hasMessage("process killed");

    assertThat(db.paidOrders()).containsExactly("o1"); // the money was taken …
    assertThat(broker.delivered()).isEmpty(); //          … and nobody will ever know ❌
  }

  @Test
  void withTheOutboxTheEventSurvivesAndIsDeliveredLater() {
    InMemoryDatabase db = new InMemoryDatabase();
    Broker broker = new Broker();

    new OutboxCheckout(db).pay("o1", false); // committed together: order + outbox row
    // (the process could die right here — the row is safely in the database)

    assertThat(new OutboxRelay(db, broker).relayOnce()).isEqualTo(1);
    assertThat(broker.delivered()).containsExactly("OrderPaid:o1"); // ✅
  }

  @Test
  void aRollbackLeavesNeitherTheChangeNorTheEvent() {
    InMemoryDatabase db = new InMemoryDatabase();

    assertThatThrownBy(() -> new OutboxCheckout(db).pay("o2", true)).hasMessage("rolled back");

    assertThat(db.paidOrders()).isEmpty();
    assertThat(db.unsentOutboxRows()).isEmpty(); // no ghost event for a change that never happened
  }

  @Test
  void aBrokerOutageOnlyDelaysDelivery() {
    InMemoryDatabase db = new InMemoryDatabase();
    Broker broker = new Broker();
    OutboxRelay relay = new OutboxRelay(db, broker);
    new OutboxCheckout(db).pay("o3", false);

    broker.setDown(true);
    assertThat(relay.relayOnce()).isZero(); // nothing lost — still unsent
    broker.setDown(false);
    assertThat(relay.relayOnce()).isEqualTo(1);

    assertThat(broker.delivered()).containsExactly("OrderPaid:o3");
    assertThat(db.unsentOutboxRows()).isEmpty();
  }
}
