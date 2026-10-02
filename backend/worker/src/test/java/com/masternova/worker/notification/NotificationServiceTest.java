package com.masternova.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.notification.UnsubscribeTokens;
import com.masternova.worker.notification.Audience.SuppressionReason;
import com.masternova.worker.notification.mail.MailProvider;
import com.masternova.worker.notification.mail.OutboundEmail;
import com.masternova.worker.notification.mail.PermanentDeliveryException;
import com.masternova.worker.notification.template.EmailTemplate;
import com.masternova.worker.notification.template.EmailTemplateEngine;
import com.masternova.worker.notification.template.VerifyEmailTemplate;
import com.masternova.worker.notification.template.WelcomeEmailTemplate;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  static final String SECRET = "test-unsubscribe-secret-0123456789abcdef";
  static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  private final InMemoryEmailDeliveries deliveries = new InMemoryEmailDeliveries();
  private final InMemoryAudience audience = new InMemoryAudience();
  private final ScriptedProvider provider = new ScriptedProvider();
  private final NotificationProperties properties =
      new NotificationProperties(
          URI.create("http://localhost:4200"),
          "Masternova <no-reply@masternova.dev>",
          NotificationProperties.Provider.SMTP,
          SECRET,
          new NotificationProperties.Resend(URI.create("https://api.resend.com"), null));
  private final NotificationService service =
      new NotificationService(
          deliveries,
          audience,
          new UnsubscribeLinks(properties, Clock.fixed(NOW, ZoneOffset.UTC)),
          provider,
          properties);
  private final VerifyEmailTemplate verify = new VerifyEmailTemplate(EmailTemplateEngine.create());

  private final WelcomeEmailTemplate welcome =
      new WelcomeEmailTemplate(EmailTemplateEngine.create());

  private final UUID eventId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final SendRequest<VerifyEmailTemplate.Payload> request =
      new SendRequest<>(
          eventId,
          verify,
          new VerifyEmailTemplate.Payload("Asha", URI.create("http://localhost:4200/v?t=1")),
          "asha@example.com",
          userId);
  private final SendRequest<WelcomeEmailTemplate.Payload> welcomeRequest =
      new SendRequest<>(
          eventId,
          welcome,
          new WelcomeEmailTemplate.Payload("Asha", URI.create("http://localhost:4200/")),
          "asha@example.com",
          userId);

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
  void aPermanentRejectionSuppressesTheAddressForEveryLaterEmail() {
    provider.failNext = () -> new PermanentDeliveryException("SMTP 550: mailbox unavailable", null);
    service.send(request);

    assertThat(audience.suppressed).containsEntry("asha@example.com", SuppressionReason.BOUNCED);
    service.send(welcomeRequest); // a different email to the same dead mailbox
    assertThat(provider.sent).isEmpty();
    assertThat(deliveries.statusOf(welcomeRequest.key())).isEqualTo("SUPPRESSED");
  }

  @Test
  void aSuppressedAddressGetsNothingNotEvenAMandatoryEmail() {
    audience.suppress("Asha@Example.com", SuppressionReason.COMPLAINED, "spam report");

    service.send(request); // ACCOUNT_SECURITY — mandatory, but the address is suppressed

    assertThat(provider.sent).isEmpty();
    assertThat(deliveries.statusOf(request.key())).isEqualTo("SUPPRESSED"); // recorded, not dropped
  }

  @Test
  void anOptOutStopsOptionalEmailsButNeverMandatoryOnes() {
    audience.optOut(userId, NotificationCategory.PRODUCT_NEWS);
    audience.optOut(userId, NotificationCategory.ACCOUNT_SECURITY); // can't happen via the api

    service.send(welcomeRequest);
    service.send(request);

    assertThat(deliveries.statusOf(welcomeRequest.key())).isEqualTo("SUPPRESSED");
    assertThat(provider.sent)
        .singleElement()
        .extracting(OutboundEmail::subject)
        .isEqualTo("Confirm your email for Masternova");
  }

  @Test
  void anOptionalEmailCarriesASignedUnsubscribeLinkAndOneClickHeaders() {
    service.send(welcomeRequest);

    OutboundEmail sent = provider.sent.getFirst();
    String token = sent.text().replaceAll("(?s).*/unsubscribe\\?token=(\\S+).*", "$1");
    assertThat(new UnsubscribeTokens(SECRET).verify(token, NOW))
        .contains(new UnsubscribeTokens.Unsubscribe(userId, NotificationCategory.PRODUCT_NEWS));
    assertThat(sent.html()).contains("/unsubscribe?token=" + token);
    assertThat(sent.headers())
        .containsEntry(
            "List-Unsubscribe",
            "<http://localhost:4200/api/v1/notifications/unsubscribe/one-click?token="
                + token
                + ">")
        .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
  }

  @Test
  void aMandatoryEmailHasNoUnsubscribeLink() {
    service.send(request);

    assertThat(provider.sent.getFirst().headers()).isEmpty();
    assertThat(provider.sent.getFirst().text()).doesNotContain("unsubscribe");
  }

  /** A template whose model blows up — stands in for any rendering bug. */
  static final class BrokenTemplate extends EmailTemplate<String> {
    BrokenTemplate() {
      super(EmailTemplateEngine.create());
    }

    @Override
    public String key() {
      return "verify-email";
    }

    @Override
    public NotificationCategory category() {
      return NotificationCategory.ACCOUNT_SECURITY;
    }

    @Override
    protected String subject(String payload) {
      return "subject";
    }

    @Override
    protected String preview(String payload) {
      return "preview";
    }

    @Override
    protected Map<String, Object> model(String payload) {
      throw new IllegalStateException("template bug");
    }
  }

  @Test
  void aRenderingProblemFailsBeforeAnythingIsClaimed() {
    SendRequest<String> broken =
        new SendRequest<>(eventId, new BrokenTemplate(), "x", "asha@example.com", userId);

    assertThatThrownBy(() -> service.send(broken)).hasMessage("template bug");
    assertThat(deliveries.rows).isEmpty(); // no half-claimed row left behind
  }
}
