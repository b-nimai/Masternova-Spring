package com.masternova.worker.notification.mail;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.worker.notification.NotificationProperties;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * ADAPTER: {@link MailProvider} over Resend's HTTP API (POST /emails, Bearer key, JSON). Selected
 * with {@code masternova.notification.provider=resend}.
 */
@Component
@ConditionalOnProperty(name = "masternova.notification.provider", havingValue = "resend")
@DesignPattern(value = Pattern.ADAPTER, role = "Adapter", note = "patterns/docs/12-adapter.md")
class ResendMailProvider implements MailProvider {

  /** Resend's request and response bodies — THEIR shapes, kept inside the adapter. */
  record SendEmailRequest(
      String from,
      List<String> to,
      String subject,
      String html,
      String text,
      Map<String, String> headers) {}

  record SendEmailResponse(String id) {}

  private final RestClient http;

  ResendMailProvider(RestClient.Builder builder, NotificationProperties properties) {
    NotificationProperties.Resend resend = properties.resend();
    if (resend.apiKey() == null || resend.apiKey().isBlank()) {
      throw new IllegalStateException("masternova.notification.resend.api-key is required");
    }
    this.http =
        builder
            .baseUrl(resend.baseUrl().toString())
            .defaultHeader("Authorization", "Bearer " + resend.apiKey())
            .build();
  }

  @Override
  public String send(OutboundEmail email) {
    try {
      SendEmailResponse response =
          http.post()
              .uri("/emails")
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  new SendEmailRequest(
                      email.from(),
                      List.of(email.to()),
                      email.subject(),
                      email.html(),
                      email.text(),
                      email.headers()))
              .retrieve()
              .body(SendEmailResponse.class);
      if (response == null || response.id() == null) {
        throw new IllegalStateException("Resend answered without an email id");
      }
      return response.id();
    } catch (RestClientResponseException e) {
      throw translate(e.getStatusCode(), e);
    }
  }

  /**
   * 422 = Resend rejected the request itself (invalid recipient) → permanent; 429 / 5xx →
   * temporary.
   */
  private static RuntimeException translate(HttpStatusCode status, RestClientResponseException e) {
    if (status.value() == 422) {
      return new PermanentDeliveryException(
          "Resend 422: " + e.getResponseBodyAsString(), e); // body says which field was invalid
    }
    return e;
  }
}
