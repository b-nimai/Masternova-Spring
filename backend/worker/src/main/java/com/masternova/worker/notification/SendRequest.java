package com.masternova.worker.notification;

import com.masternova.worker.notification.template.EmailTemplate;
import java.util.Objects;
import java.util.UUID;

/**
 * One email the pipeline is asked to send. The template and its payload travel together, typed: a
 * handler can't pair the welcome email with the verification email's data.
 *
 * @param eventId the outbox message that caused it (part of the delivery key)
 * @param userId the recipient's account — used for preferences and the unsubscribe link
 */
public record SendRequest<P>(
    UUID eventId, EmailTemplate<P> template, P payload, String to, UUID userId) {

  public SendRequest {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(template, "template");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(userId, "userId");
  }

  DeliveryKey key() {
    return new DeliveryKey(eventId, template.key(), to);
  }
}
