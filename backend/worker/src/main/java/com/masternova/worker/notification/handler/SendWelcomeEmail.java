package com.masternova.worker.notification.handler;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.worker.notification.NotificationProperties;
import com.masternova.worker.notification.NotificationService;
import com.masternova.worker.notification.SendRequest;
import com.masternova.worker.notification.template.WelcomeEmailTemplate;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * identity.email-verified.v1 → "Welcome to Masternova". PRODUCT_NEWS, so the pipeline adds an
 * unsubscribe link and skips users who opted out — this handler doesn't need to know.
 */
@Component
@DesignPattern(value = Pattern.OBSERVER, role = "Observer", note = "patterns/docs/07-observer.md")
class SendWelcomeEmail implements OutboxHandler {

  /** The consumer's view of identity's {@code EmailVerified}: only what the email needs. */
  record EmailVerifiedView(String userId, String email, String displayName) {}

  private final NotificationService notifications;
  private final WelcomeEmailTemplate template;
  private final JsonMapper json;
  private final URI webUrl;

  SendWelcomeEmail(
      NotificationService notifications,
      WelcomeEmailTemplate template,
      JsonMapper json,
      NotificationProperties properties) {
    this.notifications = notifications;
    this.template = template;
    this.json = json;
    this.webUrl = properties.webUrl();
  }

  @Override
  public String eventType() {
    return "identity.email-verified.v1";
  }

  @Override
  public void handle(OutboxMessage message) {
    EmailVerifiedView event = json.readValue(message.payload(), EmailVerifiedView.class);
    notifications.send(
        new SendRequest<>(
            message.id(),
            template,
            // the catalog arrives in Phase 5; until then "start here" is the home page
            new WelcomeEmailTemplate.Payload(event.displayName(), webUrl.resolve("/")),
            event.email(),
            UUID.fromString(event.userId())));
  }
}
