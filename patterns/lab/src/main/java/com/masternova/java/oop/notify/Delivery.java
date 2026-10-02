package com.masternova.java.oop.notify;

import java.time.Instant;

/** What happened to a notification. */
public sealed interface Delivery {
  record Sent(String channel, int attempts) implements Delivery {}

  record Deferred(Instant until) implements Delivery {}

  record Failed(String reason) implements Delivery {}
}
