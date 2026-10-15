package com.masternova.api.commerce.web;

import com.masternova.api.commerce.application.CheckoutService;
import com.masternova.api.commerce.application.OrderQueries;
import com.masternova.api.commerce.web.dto.CartRequest;
import com.masternova.api.commerce.web.dto.CheckoutResponse;
import com.masternova.api.commerce.web.dto.ConfirmPaymentRequest;
import com.masternova.api.commerce.web.dto.OrderResponse;
import com.masternova.api.identity.CurrentUser;
import com.masternova.api.platform.IdempotencyKeyRequired;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A buyer's checkout and orders. Thin: parse, call one service, map. */
@RestController
class CheckoutController {

  private final CheckoutService checkout;
  private final OrderQueries orders;

  CheckoutController(CheckoutService checkout, OrderQueries orders) {
    this.checkout = checkout;
    this.orders = orders;
  }

  /**
   * ⭐ {@code @IdempotencyKeyRequired}: a double click or a retried request replays the first answer
   * (same order), instead of opening a second order with the provider.
   */
  @PostMapping("/api/v1/checkout")
  @IdempotencyKeyRequired
  ResponseEntity<CheckoutResponse> checkout(
      CurrentUser me, @Valid @RequestBody CartRequest request) {
    var placed =
        checkout.checkout(me.id(), request.courseIds(), Optional.ofNullable(request.couponCode()));
    return ResponseEntity.created(URI.create("/api/v1/orders/" + placed.order().id()))
        .body(CheckoutResponse.from(placed));
  }

  /** Naturally idempotent (the state machine ignores a second capture): no key needed. */
  @PostMapping("/api/v1/orders/{id}/confirm")
  OrderResponse confirm(
      CurrentUser me, @PathVariable UUID id, @Valid @RequestBody ConfirmPaymentRequest request) {
    return OrderResponse.from(
        checkout.confirm(me.id(), id, request.paymentId(), request.signature()));
  }

  @GetMapping("/api/v1/orders")
  List<OrderResponse> mine(CurrentUser me) {
    return orders.mine(me.id()).stream().map(OrderResponse::from).toList();
  }

  @GetMapping("/api/v1/orders/{id}")
  OrderResponse one(CurrentUser me, @PathVariable UUID id) {
    return OrderResponse.from(orders.mine(me.id(), id));
  }
}
