package com.masternova.worker.notification.handler;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.worker.notification.NotificationProperties;
import com.masternova.worker.notification.NotificationService;
import com.masternova.worker.notification.SendRequest;
import com.masternova.worker.notification.template.VerifyEmailTemplate;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * identity.user-registered.v1 → "Confirm your email". A durable OBSERVER: identity never learns
 * this exists; the relay finds it by {@link #eventType()} (the registry, pattern 09).
 */
@Component
@DesignPattern(value = Pattern.OBSERVER, role = "Observer", note = "patterns/docs/07-observer.md")
class SendVerificationEmail implements OutboxHandler {

  /**
   * ⭐ The CONSUMER's view of the event — only the fields it needs. The producer (identity's {@code
   * UserRegistered}) can add fields without breaking this; the worker doesn't import api classes.
   */
  record UserRegisteredView(
      String userId, String email, String displayName, String verificationToken) {}

  private final NotificationService notifications;
  private final VerifyEmailTemplate template;
  private final JsonMapper json;
  private final URI webUrl;

  SendVerificationEmail(
      NotificationService notifications,
      VerifyEmailTemplate template,
      JsonMapper json,
      NotificationProperties properties) {
    this.notifications = notifications;
    this.template = template;
    this.json = json;
    this.webUrl = properties.webUrl();
  }

  @Override
  public String eventType() {
    return "identity.user-registered.v1";
  }

  @Override
  public void handle(OutboxMessage message) {
    UserRegisteredView event = json.readValue(message.payload(), UserRegisteredView.class);
    URI verifyUrl =
        URI.create(
            webUrl
                + "/verify-email?token="
                + URLEncoder.encode(event.verificationToken(), StandardCharsets.UTF_8));
    notifications.send(
        new SendRequest<>(
            message.id(),
            template,
            new VerifyEmailTemplate.Payload(event.displayName(), verifyUrl),
            event.email(),
            UUID.fromString(event.userId())));
  }
}
