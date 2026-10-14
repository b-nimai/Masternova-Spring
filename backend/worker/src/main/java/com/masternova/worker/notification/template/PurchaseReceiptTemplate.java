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
 * The receipt after an order is paid (commerce's {@code OrderPaid}). PURCHASE: part of the
 * transaction, so it ignores preferences and has no unsubscribe link.
 */
@Component
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "ConcreteClass",
    note = "patterns/docs/06-template-method.md")
public class PurchaseReceiptTemplate extends EmailTemplate<PurchaseReceiptTemplate.Payload> {

  /** One course, its price already formatted ("INR 499.00"): money is formatted by the sender. */
  public record Line(String title, String price) {}

  public record Payload(String displayName, List<Line> lines, String total, URI ordersUrl) {
    public Payload {
      lines = List.copyOf(lines);
    }
  }

  public PurchaseReceiptTemplate(ITemplateEngine engine) {
    super(engine);
  }

  @Override
  public String key() {
    return "purchase-receipt";
  }

  @Override
  public NotificationCategory category() {
    return NotificationCategory.PURCHASE;
  }

  @Override
  protected String subject(Payload payload) {
    return payload.lines().size() == 1
        ? "Your receipt: " + payload.lines().getFirst().title()
        : "Your receipt: " + payload.lines().size() + " courses";
  }

  @Override
  protected String preview(Payload payload) {
    return "Paid " + payload.total() + ". Your courses are ready.";
  }

  @Override
  protected Map<String, Object> model(Payload payload) {
    return Map.of(
        "displayName", payload.displayName(),
        "lines", payload.lines(),
        "total", payload.total(),
        "ordersUrl", payload.ordersUrl().toString());
  }
}
