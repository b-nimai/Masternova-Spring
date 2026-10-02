package com.masternova.worker.notification;

import java.util.Objects;
import java.util.UUID;

/**
 * What makes an email unique: the event that caused it, the template, and the recipient. Two
 * deliveries of the same outbox message produce the same key — that's how redelivery is caught.
 */
public record DeliveryKey(UUID eventId, String template, String recipient) {

  public DeliveryKey {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(template, "template");
    Objects.requireNonNull(recipient, "recipient");
  }
}
