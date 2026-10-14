package com.masternova.worker.notification.handler;

import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.worker.notification.NotificationProperties;
import com.masternova.worker.notification.NotificationService;
import com.masternova.worker.notification.SendRequest;
import com.masternova.worker.notification.template.RefundNoticeTemplate;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** commerce.order-refunded.v1 → "your refund is on its way". An OBSERVER of the refund. */
@Component
@DesignPattern(value = Pattern.OBSERVER, role = "Observer", note = "patterns/docs/07-observer.md")
class SendRefundNotice implements OutboxHandler {

  record OrderRefundedView(
      String userId,
      String email,
      String displayName,
      List<Item> items,
      long amountMinor,
      String currency,
      boolean accessRevoked) {
    record Item(String title) {}
  }

  private final NotificationService notifications;
  private final RefundNoticeTemplate template;
  private final JsonMapper json;
  private final URI ordersUrl;

  SendRefundNotice(
      NotificationService notifications,
      RefundNoticeTemplate template,
      JsonMapper json,
      NotificationProperties properties) {
    this.notifications = notifications;
    this.template = template;
    this.json = json;
    this.ordersUrl = properties.webUrl().resolve("/account/orders");
  }

  @Override
  public String eventType() {
    return "commerce.order-refunded.v1";
  }

  @Override
  public void handle(OutboxMessage message) {
    OrderRefundedView event = json.readValue(message.payload(), OrderRefundedView.class);
    notifications.send(
        new SendRequest<>(
            message.id(),
            template,
            new RefundNoticeTemplate.Payload(
                event.displayName(),
                event.items().stream().map(OrderRefundedView.Item::title).toList(),
                Money.of(event.amountMinor(), event.currency()).display(),
                event.accessRevoked(),
                ordersUrl),
            event.email(),
            UUID.fromString(event.userId())));
  }
}
