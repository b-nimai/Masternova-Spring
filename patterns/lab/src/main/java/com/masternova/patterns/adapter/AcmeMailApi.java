package com.masternova.patterns.adapter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ADAPTEE #2 — a second "vendor SDK", shaped differently again: a JSON-ish map in, an HTTP-like
 * {@link Response} out (status + body), and no exceptions at all.
 */
public final class AcmeMailApi {

  public record Response(int status, Map<String, Object> body) {}

  private final Set<String> invalidRecipients;
  private boolean outage;
  private int counter;

  public AcmeMailApi(Set<String> invalidRecipients) {
    this.invalidRecipients = Set.copyOf(invalidRecipients);
  }

  public void simulateOutage() {
    outage = true;
  }

  @SuppressWarnings("unchecked")
  public Response postEmails(Map<String, Object> json) {
    List<String> to = (List<String>) json.get("to");
    if (to.stream().anyMatch(invalidRecipients::contains)) {
      return new Response(422, Map.of("message", "invalid `to` field"));
    }
    if (outage) {
      return new Response(503, Map.of("message", "try again later"));
    }
    return new Response(200, Map.of("id", "acme-" + (++counter)));
  }
}
