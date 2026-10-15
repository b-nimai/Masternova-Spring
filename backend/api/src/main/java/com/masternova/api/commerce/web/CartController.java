package com.masternova.api.commerce.web;

import com.masternova.api.commerce.application.PricingService;
import com.masternova.api.commerce.web.dto.CartRequest;
import com.masternova.api.commerce.web.dto.QuoteResponse;
import com.masternova.api.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cart lives in the browser (LLD §6); the server prices it. ⭐ A POST because the cart is a
 * body, but SAFE: nothing is stored, so no Idempotency-Key. Signed-in only: "already owned" depends
 * on who asks.
 */
@RestController
@RequestMapping("/api/v1/cart")
class CartController {

  private final PricingService pricing;

  CartController(PricingService pricing) {
    this.pricing = pricing;
  }

  @PostMapping("/quote")
  QuoteResponse quote(CurrentUser me, @Valid @RequestBody CartRequest request) {
    return QuoteResponse.from(
        pricing.quote(me.id(), request.courseIds(), Optional.ofNullable(request.couponCode())));
  }
}
