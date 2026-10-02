package com.masternova.worker.notification.mail;

import java.util.Map;
import java.util.Objects;

/**
 * OUR shape of an email — what the domain wants sent, independent of any provider's API.
 *
 * @param headers extra headers, e.g. {@code List-Unsubscribe} for one-click unsubscribe in Gmail
 */
public record OutboundEmail(
    String from, String to, String subject, String html, String text, Map<String, String> headers) {

  public OutboundEmail {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(html, "html");
    Objects.requireNonNull(text, "text");
    headers = Map.copyOf(headers);
  }
}
