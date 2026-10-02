package com.masternova.java.oop.notify;

import java.util.Objects;

/** A message to one learner. */
public record Notification(String recipient, String subject, String body) {
  public Notification {
    Objects.requireNonNull(recipient, "recipient");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(body, "body");
  }
}
