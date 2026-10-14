package com.masternova.api.commerce;

import com.masternova.kernel.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * An order was paid and its courses granted — in the SAME transaction (ADR-0019), so a consumer
 * never sees a paid order without access. Through the outbox: the worker emails the receipt; Phase
 * 10's enrollment listens too. Carries the buyer's address because the worker can't look it up.
 */
public record OrderPaid(
    String orderId,
    String userId,
    String email,
    String displayName,
    List<Item> items,
    long totalMinor,
    String currency,
    Instant paidAt)
    implements DomainEvent {

  public static final String TYPE = "commerce.order-paid.v1";

  /** One course as sold. */
  public record Item(String courseId, String slug, String title, long priceMinor) {}

  public OrderPaid {
    items = List.copyOf(items);
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String aggregateId() {
    return orderId;
  }
}
