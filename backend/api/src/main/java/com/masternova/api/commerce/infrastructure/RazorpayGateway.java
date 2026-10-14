package com.masternova.api.commerce.infrastructure;

import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.commerce.domain.PaymentProviderException;
import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay's REST API behind our {@link PaymentGateway} port: an ADAPTER (LLD §6).
 *
 * <p>⭐ Force: Razorpay speaks its own JSON (amounts in paise as {@code amount}, ids prefixed {@code
 * order_}/{@code pay_}/{@code rfnd_}), its own two signature schemes, and its own errors. Commerce
 * speaks {@link Money}, {@code PaymentEvent} and {@link PaymentProviderException}. Every
 * translation lives here, so swapping the provider (or its API version) touches one class.
 *
 * <p>Error contract: a timeout, a connection failure, a 5xx or a 429 is TEMPORARY (503 to the
 * learner, retry later); any other 4xx is PERMANENT (502: our request is wrong, a human looks).
 */
@DesignPattern(
    value = Pattern.ADAPTER,
    role = "Adapter: Razorpay's REST API → PaymentGateway",
    note = "patterns/docs/12-adapter.md")
class RazorpayGateway implements PaymentGateway {

  private final RestClient http;
  private final String keyId;
  private final RazorpaySignatures signatures;
  private final RazorpayWebhooks webhooks;
  private final JsonMapper json;

  /**
   * @param http already pointed at Razorpay's base url, with basic auth and timeouts
   */
  RazorpayGateway(RestClient http, String keyId, RazorpaySignatures signatures, JsonMapper json) {
    this.http = http;
    this.keyId = keyId;
    this.signatures = signatures;
    this.webhooks = new RazorpayWebhooks(signatures, json);
    this.json = json;
  }

  @Override
  public String provider() {
    return "RAZORPAY";
  }

  @Override
  public String publicKey() {
    return keyId;
  }

  @Override
  public String createOrder(UUID orderId, Money amount) {
    return post(
            "/v1/orders",
            Map.of(
                "amount", amount.amountMinor(), // ⭐ Razorpay takes paise too: no conversion
                "currency", amount.currency().getCurrencyCode(),
                "receipt", orderId.toString(), // ≤ 40 chars: a UUID is 36
                "notes", Map.of("orderId", orderId.toString())))
        .path("id")
        .asString();
  }

  @Override
  public boolean isAuthentic(CheckoutCallback callback) {
    return signatures.isCheckoutAuthentic(
        callback.providerOrderId(), callback.paymentId(), callback.signature());
  }

  @Override
  public WebhookEvent parseWebhook(byte[] body, Map<String, String> headers) {
    return webhooks.parse(body, headers);
  }

  @Override
  public String refund(String paymentId, Money amount, UUID orderId) {
    return post(
            "/v1/payments/" + paymentId + "/refund",
            Map.of(
                "amount", amount.amountMinor(),
                "receipt", orderId.toString(),
                "notes", Map.of("orderId", orderId.toString())))
        .path("id")
        .asString();
  }

  private JsonNode post(String path, Map<String, Object> body) {
    try {
      String response =
          http.post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .body(json.writeValueAsString(body))
              .retrieve()
              .body(String.class);
      JsonNode node;
      try {
        node = json.readTree(response == null ? "{}" : response);
      } catch (RuntimeException unreadable) { // a 2xx we can't read: our contract, not Jackson's
        throw new PaymentProviderException(
            "Razorpay answered " + path + " with no JSON", false, unreadable);
      }
      if (!node.path("id").isString()) {
        throw new PaymentProviderException("Razorpay answered without an id: " + path, false, null);
      }
      return node;
    } catch (RestClientResponseException e) {
      throw new PaymentProviderException(
          "Razorpay " + path + " → " + e.getStatusCode().value() + " " + errorOf(e),
          isTemporary(e.getStatusCode()),
          e);
    } catch (ResourceAccessException e) { // timeouts, refused connections, DNS
      throw new PaymentProviderException("Razorpay unreachable: " + e.getMessage(), true, e);
    }
  }

  private static boolean isTemporary(HttpStatusCode status) {
    return status.is5xxServerError() || status.value() == 429;
  }

  /** Razorpay's {@code {"error": {"code": …, "description": …}}}, for the log line. */
  private String errorOf(RestClientResponseException e) {
    try {
      JsonNode error = json.readTree(e.getResponseBodyAsString()).path("error");
      return error.path("code").asString("") + ": " + error.path("description").asString("");
    } catch (RuntimeException unreadable) {
      return "(unreadable body)";
    }
  }
}
