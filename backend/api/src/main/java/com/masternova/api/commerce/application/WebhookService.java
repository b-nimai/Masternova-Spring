package com.masternova.api.commerce.application;

import com.masternova.api.commerce.application.PaymentEvents.Applied;
import com.masternova.api.commerce.domain.PaymentGateway;
import com.masternova.api.commerce.domain.PaymentGateway.WebhookEvent;
import com.masternova.api.commerce.domain.Transition;
import com.masternova.api.commerce.domain.WebhookInbox;
import com.masternova.api.commerce.domain.WebhookInbox.Outcome;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A provider's webhook, exactly once in effect (ADR-0018, LLD §5): verify the signature over the
 * raw body, then — in ONE transaction — claim the event id, apply the event to its order, record
 * the outcome. If anything throws, the claim rolls back with the effects and the provider's
 * redelivery claims it again.
 */
@Service
public class WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

  private final PaymentGateway gateway;
  private final WebhookInbox inbox;
  private final PaymentEvents payments;
  private final Clock clock;

  WebhookService(PaymentGateway gateway, WebhookInbox inbox, PaymentEvents payments, Clock clock) {
    this.gateway = gateway;
    this.inbox = inbox;
    this.payments = payments;
    this.clock = clock;
  }

  /** What happened to one delivery; every one of them is a 200 (API conventions §12). */
  public enum Result {
    PROCESSED,
    IGNORED,
    UNKNOWN_ORDER,
    /** Already processed: this copy did nothing. */
    DUPLICATE
  }

  /**
   * @throws PaymentGateway.InvalidSignatureException before anything is read or claimed
   */
  @Transactional
  public Result handle(byte[] body, Map<String, String> headers) {
    WebhookEvent webhook = gateway.parseWebhook(body, headers);
    String provider = gateway.provider();
    if (!inbox.claim(
        provider, webhook.eventId(), webhook.type(), webhook.providerOrderId(), clock.instant())) {
      log.info("Webhook {} ({}) already processed", webhook.eventId(), webhook.type());
      return Result.DUPLICATE;
    }
    Outcome outcome = apply(webhook);
    inbox.record(provider, webhook.eventId(), outcome);
    log.info("Webhook {} ({}) → {}", webhook.eventId(), webhook.type(), outcome);
    return switch (outcome) { // exhaustive: a new Outcome is a compile error here, not a 500
      case PROCESSED -> Result.PROCESSED;
      case IGNORED -> Result.IGNORED;
      case UNKNOWN_ORDER -> Result.UNKNOWN_ORDER;
    };
  }

  private Outcome apply(WebhookEvent webhook) {
    if (webhook.event().isEmpty() || webhook.providerOrderId().isEmpty()) {
      return Outcome.IGNORED; // a type we don't act on
    }
    Optional<Applied> applied =
        payments.applyByProviderOrder(webhook.providerOrderId().get(), webhook.event().get());
    return applied
        .map(a -> a.transition() instanceof Transition.Move ? Outcome.PROCESSED : Outcome.IGNORED)
        .orElse(Outcome.UNKNOWN_ORDER);
  }
}
