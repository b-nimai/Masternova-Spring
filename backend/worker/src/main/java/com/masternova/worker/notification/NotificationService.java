package com.masternova.worker.notification;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.worker.notification.Audience.SuppressionReason;
import com.masternova.worker.notification.EmailDeliveries.Claim;
import com.masternova.worker.notification.mail.MailProvider;
import com.masternova.worker.notification.mail.OutboundEmail;
import com.masternova.worker.notification.mail.PermanentDeliveryException;
import com.masternova.worker.notification.template.RenderContext;
import com.masternova.worker.notification.template.RenderedEmail;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The send pipeline (docs/lld/notification.md §5): consent → render → claim → send → record. Safe
 * to call any number of times for the same event — the delivery claim decides whether anything is
 * sent.
 *
 * <p>Deliberately NOT {@code @Transactional}: the SMTP/HTTP call can take seconds, and no database
 * connection may be held while it runs. Each repository call is its own short statement.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final EmailDeliveries deliveries;
  private final Audience audience;
  private final UnsubscribeLinks unsubscribeLinks;
  private final MailProvider mail;
  private final NotificationProperties properties;

  NotificationService(
      EmailDeliveries deliveries,
      Audience audience,
      UnsubscribeLinks unsubscribeLinks,
      MailProvider mail,
      NotificationProperties properties) {
    this.deliveries = deliveries;
    this.audience = audience;
    this.unsubscribeLinks = unsubscribeLinks;
    this.mail = mail;
    this.properties = properties;
  }

  public <P> void send(SendRequest<P> request) {
    DeliveryKey key = request.key();
    NotificationCategory category = request.template().category();

    // ⭐ consent first, in this order: a suppressed ADDRESS outranks everything — even a mandatory
    //    email (mailing a dead mailbox again hurts deliverability for every user); a preference
    //    only applies to opt-out-able categories.
    Optional<String> refusal = refusal(request, category);
    if (refusal.isPresent()) {
      deliveries.recordSuppressed(key, refusal.get()); // recorded, not silently dropped
      log.info("{} for event {} not sent: {}", key.template(), key.eventId(), refusal.get());
      return;
    }

    Optional<UnsubscribeLinks.Link> unsubscribe =
        unsubscribeLinks.forRecipient(request.userId(), category);
    // ⭐ render BEFORE claiming: a template bug fails the handler (→ retry → DEAD) without leaving
    //    a claimed-but-never-sent row behind
    RenderedEmail email =
        request
            .template()
            .render(
                request.payload(),
                new RenderContext(
                    properties.webUrl(), unsubscribe.map(UnsubscribeLinks.Link::page)));
    OutboundEmail outbound =
        new OutboundEmail(
            properties.from(),
            request.to(),
            email.subject(),
            email.html(),
            email.text(),
            unsubscribe.map(UnsubscribeLinks.Link::headers).orElse(Map.of()));

    switch (deliveries.claim(key)) {
      case Claim.Claimed claimed -> deliver(claimed, outbound);
      case Claim.AlreadySent sent ->
          log.info(
              "{} for event {} was already sent; nothing to do", key.template(), key.eventId());
      case Claim.Final finalState ->
          log.info("{} for event {} is {}; not sending", key.template(), key.eventId(), finalState);
      case Claim.InFlight inFlight -> throw new DeliveryInFlightException(key);
    }
  }

  private Optional<String> refusal(SendRequest<?> request, NotificationCategory category) {
    if (audience.isSuppressed(request.to())) {
      return Optional.of("address suppressed");
    }
    if (!category.mandatory() && audience.hasOptedOut(request.userId(), category)) {
      return Optional.of("opted out of " + category);
    }
    return Optional.empty();
  }

  private void deliver(Claim.Claimed claimed, OutboundEmail outbound) {
    try {
      String providerMessageId = mail.send(outbound);
      deliveries.markSent(claimed.deliveryId(), providerMessageId);
    } catch (PermanentDeliveryException e) {
      // retrying can never succeed: record it, stop mailing the address, and return normally —
      // the outbox message is DONE
      deliveries.markBounced(claimed.deliveryId(), e.getMessage());
      audience.suppress(outbound.to(), SuppressionReason.BOUNCED, e.getMessage());
      log.warn(
          "{} to {} bounced permanently: {}", outbound.subject(), outbound.to(), e.getMessage());
    } catch (RuntimeException e) {
      deliveries.markFailed(
          claimed.deliveryId(), e.getClass().getSimpleName() + ": " + e.getMessage());
      throw e; // temporary: the outbox reschedules the message with backoff
    }
  }
}
