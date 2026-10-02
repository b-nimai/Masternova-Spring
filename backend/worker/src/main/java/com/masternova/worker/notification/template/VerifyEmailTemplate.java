package com.masternova.worker.notification.template;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;

/** "Confirm your email" — sent right after signup. Mandatory (account security). */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
public class VerifyEmailTemplate extends EmailTemplate<VerifyEmailTemplate.Payload> {

  /** What this email needs to know. */
  public record Payload(String displayName, URI verifyUrl) {}

  public VerifyEmailTemplate(ITemplateEngine emailTemplateEngine) {
    super(emailTemplateEngine);
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
  protected String subject(Payload payload) {
    return "Confirm your email for Masternova";
  }

  @Override
  protected String preview(Payload payload) {
    return "One click and your account is ready.";
  }

  @Override
  protected Map<String, Object> model(Payload payload) {
    return Map.of(
        "displayName", payload.displayName(), "verifyUrl", payload.verifyUrl().toString());
  }
}
