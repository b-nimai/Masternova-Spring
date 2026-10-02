package com.masternova.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.worker.notification.mail.MailProvider;
import com.masternova.worker.notification.mail.OutboundEmail;
import com.masternova.worker.notification.mail.PermanentDeliveryException;
import com.masternova.worker.notification.template.EmailTemplateEngine;
import com.masternova.worker.notification.template.VerifyEmailTemplate;
import com.masternova.worker.notification.template.WelcomeEmailTemplate;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The pipeline's decisions, with no database and no SMTP: fakes for both. */
class NotificationServiceTest {

  /** A provider whose next answer the test decides. */
  static final class ScriptedProvider implements MailProvider {
    final List<OutboundEmail> sent = new ArrayList<>();
    Supplier<RuntimeException> failNext;

    @Override
    public String send(OutboundEmail email) {
      if (failNext != null) {
        RuntimeException failure = failNext.get();
        failNext = null;
        throw failure;
      }
      sent.add(email);
      return "<msg-" + sent.size() + ">";
    }
  }

  private final InMemoryEmailDeliveries deliveries = new InMemoryEmailDeliveries();
  private final ScriptedProvider provider = new ScriptedProvider();
  private final NotificationProperties properties =
      new NotificationProperties(
          URI.create("http://localhost:4200"),
          "Masternova <no-reply@masternova.dev>",
          NotificationProperties.Provider.SMTP,
          new NotificationProperties.Resend(URI.create("https://api.resend.com"), null));
  private final NotificationService service =
      new NotificationService(deliveries, provider, properties);
  private final VerifyEmailTemplate verify = new VerifyEmailTemplate(EmailTemplateEngine.create());

  private final UUID eventId = UUID.randomUUID();
  private final SendRequest<VerifyEmailTemplate.Payload> request =
      new SendRequest<>(
          eventId,
          verify,
          new VerifyEmailTemplate.Payload("Asha", URI.create("http://localhost:4200/v?t=1")),
          "asha@example.com",
          UUID.randomUUID());

  @Test
  void sendsOnceEvenWhenTheEventIsDeliveredThreeTimes() {
    service.send(request);
    service.send(request); // ⭐ at-least-once delivery from the outbox …
    service.send(request);

    assertThat(provider.sent).hasSize(1); // … exactly one email
    assertThat(provider.sent.getFirst())
        .satisfies(
            email -> {
              assertThat(email.to()).isEqualTo("asha@example.com");
              assertThat(email.from()).isEqualTo("Masternova <no-reply@masternova.dev>");
              assertThat(email.subject()).isEqualTo("Confirm your email for Masternova");
              assertThat(email.text()).contains("http://localhost:4200/v?t=1");
            });
    assertThat(deliveries.statusOf(request.key())).isEqualTo("SENT");
  }

  @Test
  void aTemporaryFailureIsRecordedRethrownAndSucceedsOnRedelivery() {
    provider.failNext = () -> new IllegalStateException("connection refused");

    assertThatThrownBy(() -> service.send(request)).hasMessage("connection refused");
    assertThat(deliveries.statusOf(request.key())).isEqualTo("FAILED");

    service.send(request); // the outbox redelivers after backoff

    assertThat(provider.sent).hasSize(1);
    assertThat(deliveries.rows.get(request.key()).attempts()).isEqualTo(2);
    assertThat(deliveries.statusOf(request.key())).isEqualTo("SENT");
  }

  @Test
  void aPermanentRejectionIsRecordedAndNotRetried() {
    provider.failNext = () -> new PermanentDeliveryException("SMTP 550: mailbox unavailable", null);

    service.send(request); // ⭐ returns normally: the outbox marks the message DONE, no retries

    assertThat(deliveries.statusOf(request.key())).isEqualTo("BOUNCED");
    service.send(request);
    assertThat(provider.sent).isEmpty(); // a bounced email is final
  }

  @Test
  void aSendInFlightElsewhereIsRetriedLater() {
    deliveries.nextClaimInFlight = true;

    assertThatThrownBy(() -> service.send(request)).isInstanceOf(DeliveryInFlightException.class);
    assertThat(provider.sent).isEmpty();
  }

  @Test
  void aRenderingProblemFailsBeforeAnythingIsClaimed() {
    WelcomeEmailTemplate welcome = new WelcomeEmailTemplate(EmailTemplateEngine.create());
    SendRequest<WelcomeEmailTemplate.Payload> optionalWithoutLink =
        new SendRequest<>(
            eventId,
            welcome,
            new WelcomeEmailTemplate.Payload("Asha", URI.create("http://localhost:4200/")),
            "asha@example.com",
            UUID.randomUUID());

    assertThatThrownBy(() -> service.send(optionalWithoutLink))
        .isInstanceOf(IllegalStateException.class);
    assertThat(deliveries.rows).isEmpty(); // no half-claimed row left behind
  }
}
