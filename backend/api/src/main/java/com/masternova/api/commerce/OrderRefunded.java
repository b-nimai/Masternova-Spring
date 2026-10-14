package com.masternova.api.commerce;

import com.masternova.kernel.event.DomainEvent;
import java.time.Instant;
import java.util.List;

/**
 * An order's money went back — and, if it had been paid, its courses were revoked in the SAME
 * transaction (ADR-0019). Through the outbox: the worker emails the refund notice.
 *
 * @param accessRevoked false when the refund overtook the capture: nothing had been granted
 */
public record OrderRefunded(
    String orderId,
    String userId,
    String email,
    String displayName,
    List<OrderPaid.Item> items,
    long amountMinor,
    String currency,
    boolean accessRevoked,
    Instant refundedAt)
    implements DomainEvent {

  public static final String TYPE = "commerce.order-refunded.v1";

  public OrderRefunded {
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
