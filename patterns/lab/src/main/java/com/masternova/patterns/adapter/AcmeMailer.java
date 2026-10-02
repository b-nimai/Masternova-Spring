package com.masternova.patterns.adapter;

import java.util.List;
import java.util.Map;

/**
 * ADAPTER #2 over {@link AcmeMailApi}: our {@link Mailer.Email} → their JSON map; their status
 * codes → our failure contract. The vendor's field names ("to" is a LIST, "text") never leak out.
 */
public final class AcmeMailer implements Mailer {

  private final AcmeMailApi api;

  public AcmeMailer(AcmeMailApi api) {
    this.api = api;
  }

  @Override
  public String send(Email email) {
    AcmeMailApi.Response response =
        api.postEmails(
            Map.of("to", List.of(email.to()), "subject", email.subject(), "text", email.body()));
    return switch (response.status()) {
      case 200 -> (String) response.body().get("id");
      case 422 -> throw new Rejected("Acme 422: " + response.body().get("message"), null);
      default ->
          throw new IllegalStateException(
              "Acme " + response.status() + ": " + response.body().get("message"));
    };
  }
}
