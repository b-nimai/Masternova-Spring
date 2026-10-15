package com.masternova.api.commerce.web;

import com.masternova.api.commerce.application.WebhookService;
import com.masternova.api.commerce.web.dto.WebhookResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The payment provider's webhook (API conventions §12). Public: authenticated by the signature over
 * the raw body, not by a token.
 *
 * <p>⭐ {@code byte[]}: the body exactly as it arrived. A DTO would be parsed and re-serialised, and
 * the HMAC is over the bytes. Every verified delivery answers 200, whatever it did; a bad signature
 * is 400 (terminal for the provider: it would fail again).
 */
@RestController
class WebhookController {

  private final WebhookService webhooks;

  WebhookController(WebhookService webhooks) {
    this.webhooks = webhooks;
  }

  @PostMapping("/api/v1/webhooks/payments")
  WebhookResponse receive(@RequestBody byte[] body, @RequestHeader HttpHeaders headers) {
    Map<String, String> lowerCased = new LinkedHashMap<>();
    headers.forEach(
        (name, values) -> lowerCased.put(name.toLowerCase(Locale.ROOT), values.getFirst()));
    return new WebhookResponse(webhooks.handle(body, lowerCased));
  }
}
