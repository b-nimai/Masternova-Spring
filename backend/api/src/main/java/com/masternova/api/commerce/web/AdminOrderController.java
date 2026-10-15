package com.masternova.api.commerce.web;

import com.masternova.api.commerce.application.OrderQueries;
import com.masternova.api.commerce.application.RefundService;
import com.masternova.api.commerce.web.dto.AdminOrderResponse;
import com.masternova.api.platform.IdempotencyKeyRequired;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Support's view of orders, and refunds. */
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
class AdminOrderController {

  private final OrderQueries orders;
  private final RefundService refunds;

  AdminOrderController(OrderQueries orders, RefundService refunds) {
    this.orders = orders;
    this.refunds = refunds;
  }

  @GetMapping
  List<AdminOrderResponse> recent(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return orders.recent(limit).stream().map(AdminOrderResponse::from).toList();
  }

  /**
   * ⭐ {@code @IdempotencyKeyRequired}: a double-clicked refund replays the first answer instead of
   * asking the provider twice (the second would be refused anyway, but as a 502).
   */
  @PostMapping("/{id}/refund")
  @IdempotencyKeyRequired
  AdminOrderResponse refund(@PathVariable UUID id) {
    return AdminOrderResponse.from(refunds.refund(id));
  }
}
