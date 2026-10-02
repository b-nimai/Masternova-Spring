package com.masternova.java.oop.notify;

import java.util.Objects;

/**
 * ⭐ DECORATOR: implements Channel AND wraps a Channel. Adds retries to ANY channel — email, SMS,
 * or a channel that is itself decorated. With inheritance this needed RetryingEmailNotifier,
 * RetryingSmsNotifier, … one subclass per combination.
 */
public final class RetryingChannel implements Channel {

  private final Channel inner;
  private final int maxAttempts;

  public RetryingChannel(Channel inner, int maxAttempts) {
    this.inner = Objects.requireNonNull(inner, "inner");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
    this.maxAttempts = maxAttempts;
  }

  @Override
  public Delivery send(Notification notification) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        Delivery delivery = inner.send(notification);
        return delivery instanceof Delivery.Sent(String channel, int _)
            ? new Delivery.Sent(channel, attempt)
            : delivery;
      } catch (RuntimeException e) {
        last = e;
      }
    }
    return new Delivery.Failed(inner.name() + " failed " + maxAttempts + " times: " + last.getMessage());
  }

  @Override
  public String name() {
    return inner.name();
  }
}
