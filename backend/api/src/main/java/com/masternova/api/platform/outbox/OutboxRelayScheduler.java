package com.masternova.api.platform.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the relay on a fixed delay. A separate bean so tests (and, in Phase 4, the api — once the
 * worker takes over) can switch the SCHEDULE off with {@code masternova.outbox.relay-enabled=false}
 * while still driving {@link OutboxRelay#relayOnce()} by hand.
 */
@Component
@ConditionalOnBooleanProperty(name = "masternova.outbox.relay-enabled", matchIfMissing = true)
class OutboxRelayScheduler {

  private final OutboxRelay relay;

  OutboxRelayScheduler(OutboxRelay relay) {
    this.relay = relay;
  }

  // fixedDelay: the next run starts poll-interval AFTER the previous one finished — never overlaps
  @Scheduled(fixedDelayString = "${masternova.outbox.poll-interval:1s}")
  void poll() {
    relay.relayOnce();
  }
}
