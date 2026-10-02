package com.masternova.messaging.outbox;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the relay on a fixed delay. Only exists where {@code masternova.outbox.relay-enabled=true}
 * (the worker); tests build an {@link OutboxRelay} themselves and call {@code relayOnce()} by hand.
 */
class OutboxRelayScheduler {

  private final OutboxRelay relay;

  OutboxRelayScheduler(OutboxRelay relay) {
    this.relay = relay;
  }

  // fixedDelay: the next run starts poll-interval AFTER the previous one finished — never overlaps.
  // The initial delay lets the application finish starting before the first claim.
  @Scheduled(
      initialDelayString = "${masternova.outbox.poll-interval:1s}",
      fixedDelayString = "${masternova.outbox.poll-interval:1s}")
  void poll() {
    relay.relayOnce();
  }
}
