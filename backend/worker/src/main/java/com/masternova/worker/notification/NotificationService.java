package com.masternova.worker.notification;

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
 * The send pipeline (docs/lld/notification.md §5): render → claim → send → record. Safe to call any
 * number of times for the same event — the delivery claim decides whether anything is sent.
 *
 * <p>Deliberately NOT {@code @Transactional}: the SMTP/HTTP call can take seconds, and no database
 * connection may be held while it runs. Each repository call is its own short statement.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final EmailDeliveries deliveries;
  private final MailProvider mail;
  private final NotificationProperties properties;

  NotificationService(
      EmailDeliveries deliveries, MailProvider mail, NotificationProperties properties) {
    this.deliveries = deliveries;
    this.mail = mail;
    this.properties = properties;
  }

  public <P> void send(SendRequest<P> request) {
    DeliveryKey key = request.key();
    // ⭐ render BEFORE claiming: a template bug fails the handler (→ retry → DEAD) without leaving
    //    a claimed-but-never-sent row behind
    RenderedEmail email =
        request
            .template()
            .render(request.payload(), new RenderContext(properties.webUrl(), Optional.empty()));

    switch (deliveries.claim(key)) {
      case Claim.Claimed claimed -> deliver(claimed, request, email);
      case Claim.AlreadySent sent ->
          log.info(
              "{} for event {} was already sent; nothing to do", key.template(), key.eventId());
      case Claim.Final finalState ->
          log.info("{} for event {} is {}; not sending", key.template(), key.eventId(), finalState);
      case Claim.InFlight inFlight -> throw new DeliveryInFlightException(key);
    }
  }

  private void deliver(Claim.Claimed claimed, SendRequest<?> request, RenderedEmail email) {
    OutboundEmail outbound =
        new OutboundEmail(
            properties.from(), request.to(), email.subject(), email.html(), email.text(), Map.of());
    try {
      String providerMessageId = mail.send(outbound);
      deliveries.markSent(claimed.deliveryId(), providerMessageId);
    } catch (PermanentDeliveryException e) {
      // retrying can never succeed: record it and return normally — the outbox message is DONE
      deliveries.markBounced(claimed.deliveryId(), e.getMessage());
      log.warn("{} to {} bounced permanently: {}", email.subject(), request.to(), e.getMessage());
    } catch (RuntimeException e) {
      deliveries.markFailed(
          claimed.deliveryId(), e.getClass().getSimpleName() + ": " + e.getMessage());
      throw e; // temporary: the outbox reschedules the message with backoff
    }
  }
}
