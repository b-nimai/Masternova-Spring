package com.masternova.patterns.registry;

import java.util.Objects;

/** What gets dispatched: a type (the registry key) and a payload. */
public record Message(String type, String payload) {

  public Message {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(payload, "payload");
  }
}
