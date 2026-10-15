package com.masternova.api.commerce.application;

import com.masternova.api.commerce.domain.Order;
import com.masternova.api.commerce.domain.OrderRepository;
import com.masternova.api.platform.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading orders: a buyer's own, and the admin's list. */
@Service
@Transactional(readOnly = true)
public class OrderQueries {

  /** Enough for an order history page; nobody has hundreds of orders. */
  static final int HISTORY_LIMIT = 100;

  private final OrderRepository orders;

  OrderQueries(OrderRepository orders) {
    this.orders = orders;
  }

  public List<Order> mine(UUID buyerId) {
    return orders.findByBuyerIdOrderByCreatedAtDesc(buyerId, Limit.of(HISTORY_LIMIT));
  }

  /** Someone else's order is "not found", never "forbidden" (API conventions §1). */
  public Order mine(UUID buyerId, UUID orderId) {
    return orders
        .findByIdAndBuyerId(orderId, buyerId)
        .orElseThrow(() -> new NotFoundException("Order", orderId));
  }

  public List<Order> recent(int limit) {
    return orders.findAllByOrderByCreatedAtDesc(Limit.of(limit));
  }
}
