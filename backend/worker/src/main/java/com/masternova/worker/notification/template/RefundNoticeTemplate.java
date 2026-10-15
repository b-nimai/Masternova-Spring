package com.masternova.worker.notification.template;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;

/**
 * "Your refund is on its way" (commerce's {@code OrderRefunded}). PURCHASE: mandatory. Says the
 * courses are no longer available only when access had actually been granted.
 */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
public class RefundNoticeTemplate extends EmailTemplate<RefundNoticeTemplate.Payload> {

  public record Payload(
      String displayName,
      List<String> titles,
      String amount,
      boolean accessRevoked,
      URI ordersUrl) {
    public Payload {
      titles = List.copyOf(titles);
    }
  }

  public RefundNoticeTemplate(ITemplateEngine engine) {
    super(engine);
  }

  @Override
  public String key() {
    return "refund-notice";
  }

  @Override
  public NotificationCategory category() {
    return NotificationCategory.PURCHASE;
  }

  @Override
  protected String subject(Payload payload) {
    return "Your refund of " + payload.amount() + " is on its way";
  }

  @Override
  protected String preview(Payload payload) {
    return "We've refunded your order. It can take 5–7 working days to reach you.";
  }

  @Override
  protected Map<String, Object> model(Payload payload) {
    return Map.of(
        "displayName", payload.displayName(),
        "titles", payload.titles(),
        "amount", payload.amount(),
        "accessRevoked", payload.accessRevoked(),
        "ordersUrl", payload.ordersUrl().toString());
  }
}
