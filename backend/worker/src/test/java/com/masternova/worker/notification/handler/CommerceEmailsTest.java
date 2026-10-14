package com.masternova.worker.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.masternova.messaging.OutboxMessage;
import com.masternova.worker.notification.NotificationProperties;
import com.masternova.worker.notification.NotificationService;
import com.masternova.worker.notification.SendRequest;
import com.masternova.worker.notification.template.EmailTemplateEngine;
import com.masternova.worker.notification.template.PurchaseReceiptTemplate;
import com.masternova.worker.notification.template.RefundNoticeTemplate;
import com.masternova.worker.notification.template.RenderContext;
import com.masternova.worker.notification.template.RenderedEmail;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two OBSERVERS of commerce (9.7), fed the outbox payloads exactly as the api writes them
 * (field for field from {@code OrderPaid} / {@code OrderRefunded}), then rendered. Proves the
 * consumer's view reads the producer's JSON, which no compiler checks across the two apps.
 */
class CommerceEmailsTest {

  static final URI WEB = URI.create("http://localhost:4200");
  final JsonMapper json = JsonMapper.builder().build();
  final NotificationService notifications = mock(NotificationService.class);
  final NotificationProperties properties =
      new NotificationProperties(
          WEB,
          "Masternova <no-reply@masternova.dev>",
          NotificationProperties.Provider.SMTP,
          "test-unsubscribe-secret-0123456789abcdef",
          new NotificationProperties.Resend(URI.create("https://api.resend.com"), null));
  final UUID userId = UUID.randomUUID();

  @SuppressWarnings("unchecked")
  private <P> SendRequest<P> sent() {
    ArgumentCaptor<SendRequest<P>> request = ArgumentCaptor.forClass(SendRequest.class);
    verify(notifications).send(request.capture());
    return request.getValue();
  }

  private OutboxMessage message(String type, String payload) {
    return new OutboxMessage(UUID.randomUUID(), type, "order-1", payload, Instant.now(), 1);
  }

  @Test
  void anOrderPaidBecomesAReceipt() {
    var template = new PurchaseReceiptTemplate(EmailTemplateEngine.create());
    var handler = new SendPurchaseReceipt(notifications, template, json, properties);
    String payload =
        """
        {"orderId": "o-1", "userId": "%s", "email": "lena@example.com", "displayName": "Lena",
         "items": [{"courseId": "c-1", "slug": "java", "title": "Java from zero", "priceMinor": 49900},
                   {"courseId": "c-2", "slug": "spring", "title": "Spring", "priceMinor": 79900}],
         "totalMinor": 109800, "currency": "INR", "paidAt": "2026-10-14T10:00:00Z"}
        """
            .formatted(userId);

    handler.handle(message(handler.eventType(), payload));

    SendRequest<PurchaseReceiptTemplate.Payload> request = sent();
    assertThat(request.to()).isEqualTo("lena@example.com");
    assertThat(request.userId()).isEqualTo(userId);
    RenderedEmail email =
        template.render(request.payload(), new RenderContext(WEB, Optional.empty()));
    assertThat(email.subject()).isEqualTo("Your receipt: 2 courses");
    assertThat(email.html()).contains("Java from zero", "INR 499.00", "INR 1098.00");
    assertThat(email.html()).contains("href=\"http://localhost:4200/account/orders\"");
    assertThat(email.text()).contains("- Spring: INR 799.00", "Total paid: INR 1098.00");
  }

  @Test
  void anOrderRefundedBecomesANoticeThatSaysWhatWasLost() {
    var template = new RefundNoticeTemplate(EmailTemplateEngine.create());
    var handler = new SendRefundNotice(notifications, template, json, properties);
    String payload =
        """
        {"orderId": "o-1", "userId": "%s", "email": "lena@example.com", "displayName": "Lena",
         "items": [{"courseId": "c-1", "slug": "java", "title": "Java from zero", "priceMinor": 49900}],
         "amountMinor": 49900, "currency": "INR", "accessRevoked": %s,
         "refundedAt": "2026-10-14T10:00:00Z"}
        """;

    handler.handle(message(handler.eventType(), payload.formatted(userId, true)));
    SendRequest<RefundNoticeTemplate.Payload> revoked = sent();
    RenderedEmail email =
        template.render(revoked.payload(), new RenderContext(WEB, Optional.empty()));
    assertThat(email.subject()).isEqualTo("Your refund of INR 499.00 is on its way");
    assertThat(email.html()).contains("Java from zero", "no longer available");
    assertThat(email.text()).contains("no longer available").doesNotContain("Unsubscribe");

    // refunded before the capture was seen: nothing was granted, so nothing to say about access
    var unpaid =
        template.render(
            new RefundNoticeTemplate.Payload(
                "Lena", java.util.List.of("Java"), "INR 499.00", false, WEB),
            new RenderContext(WEB, Optional.empty()));
    assertThat(unpaid.html()).doesNotContain("no longer available");
    assertThat(unpaid.text()).doesNotContain("no longer available");
  }

  @Test
  void bothAreMandatoryPurchaseEmails() {
    var receipt = new PurchaseReceiptTemplate(EmailTemplateEngine.create());
    assertThat(receipt.category().mandatory()).isTrue();
    assertThat(new RefundNoticeTemplate(EmailTemplateEngine.create()).category())
        .isEqualTo(receipt.category());
    verify(notifications, org.mockito.Mockito.never()).send(any());
  }
}
