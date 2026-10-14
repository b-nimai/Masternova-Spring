package com.masternova.worker.notification.handler;

import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.worker.notification.NotificationProperties;
import com.masternova.worker.notification.NotificationService;
import com.masternova.worker.notification.SendRequest;
import com.masternova.worker.notification.template.PurchaseReceiptTemplate;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** commerce.order-paid.v1 → the receipt. An OBSERVER: commerce doesn't know emails exist. */
@Component
@DesignPattern(value = Pattern.OBSERVER, role = "Observer", note = "patterns/docs/07-observer.md")
class SendPurchaseReceipt implements OutboxHandler {

  /** The consumer's view of commerce's {@code OrderPaid}: only what the receipt needs. */
  record OrderPaidView(
      String userId,
      String email,
      String displayName,
      List<Item> items,
      long totalMinor,
      String currency) {
    record Item(String title, long priceMinor) {}
  }

  private final NotificationService notifications;
  private final PurchaseReceiptTemplate template;
  private final JsonMapper json;
  private final URI ordersUrl;

  SendPurchaseReceipt(
      NotificationService notifications,
      PurchaseReceiptTemplate template,
      JsonMapper json,
      NotificationProperties properties) {
    this.notifications = notifications;
    this.template = template;
    this.json = json;
    this.ordersUrl = properties.webUrl().resolve("/account/orders");
  }

  @Override
  public String eventType() {
    return "commerce.order-paid.v1";
  }

  @Override
  public void handle(OutboxMessage message) {
    OrderPaidView event = json.readValue(message.payload(), OrderPaidView.class);
    notifications.send(
        new SendRequest<>(
            message.id(),
            template,
            new PurchaseReceiptTemplate.Payload(
                event.displayName(),
                event.items().stream()
                    .map(
                        i ->
                            new PurchaseReceiptTemplate.Line(
                                i.title(), Money.of(i.priceMinor(), event.currency()).display()))
                    .toList(),
                Money.of(event.totalMinor(), event.currency()).display(),
                ordersUrl),
            event.email(),
            UUID.fromString(event.userId())));
  }
}
