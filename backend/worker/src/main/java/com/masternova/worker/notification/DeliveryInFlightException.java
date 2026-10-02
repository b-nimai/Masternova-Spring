package com.masternova.worker.notification;

/**
 * Another process holds a fresh claim on this exact email. Thrown so the outbox retries later: by
 * then the other send has finished (SENT → nothing to do) or its lease ran out (re-claimable).
 */
public class DeliveryInFlightException extends RuntimeException {

  public DeliveryInFlightException(DeliveryKey key) {
    super("email " + key.template() + " for event " + key.eventId() + " is being sent elsewhere");
  }
}
