package com.masternova.worker.notification.template;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;

/**
 * "Welcome to Masternova" — after the email is verified. PRODUCT_NEWS on purpose: onboarding the
 * user didn't ask for, so it carries a real unsubscribe link from the very first email.
 */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
public class WelcomeEmailTemplate extends EmailTemplate<WelcomeEmailTemplate.Payload> {

  public record Payload(String displayName, URI browseUrl) {}

  public WelcomeEmailTemplate(ITemplateEngine emailTemplateEngine) {
    super(emailTemplateEngine);
  }

  @Override
  public String key() {
    return "welcome";
  }

  @Override
  public NotificationCategory category() {
    return NotificationCategory.PRODUCT_NEWS;
  }

  @Override
  protected String subject(Payload payload) {
    return "Welcome to Masternova, " + payload.displayName();
  }

  @Override
  protected String preview(Payload payload) {
    return "Your account is verified. Here's where to start.";
  }

  @Override
  protected Map<String, Object> model(Payload payload) {
    return Map.of(
        "displayName", payload.displayName(), "browseUrl", payload.browseUrl().toString());
  }
}
