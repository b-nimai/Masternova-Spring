package com.masternova.api.commerce.web;

import com.masternova.api.commerce.application.CouponAdminService;
import com.masternova.api.commerce.domain.Coupon;
import com.masternova.api.commerce.web.dto.CouponResponse;
import com.masternova.api.commerce.web.dto.CreateCouponRequest;
import com.masternova.kernel.money.Money;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coupons are an admin's. ⭐ Create needs no Idempotency-Key: the code is the natural key, so a
 * retried create is a 409 {@code COUPON_CODE_TAKEN}, never a second coupon.
 */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
class AdminCouponController {

  private final CouponAdminService coupons;

  AdminCouponController(CouponAdminService coupons) {
    this.coupons = coupons;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CouponResponse create(@Valid @RequestBody CreateCouponRequest r) {
    var currency = CouponAdminService.CURRENCY;
    return CouponResponse.from(
        coupons.create(
            r.code(),
            new Coupon.Terms(
                Optional.ofNullable(r.percentOff()),
                Optional.ofNullable(r.amountOffMinor()).map(m -> new Money(m, currency)),
                Optional.ofNullable(r.maxDiscountMinor()).map(m -> new Money(m, currency)),
                new Money(Optional.ofNullable(r.minSubtotalMinor()).orElse(0L), currency),
                Optional.ofNullable(r.courseId()),
                Optional.ofNullable(r.startsAt()),
                Optional.ofNullable(r.endsAt()),
                Optional.ofNullable(r.maxRedemptions()))));
  }

  @GetMapping
  List<CouponResponse> list() {
    return coupons.list().stream().map(CouponResponse::from).toList();
  }

  @PostMapping("/{code}/deactivate")
  CouponResponse deactivate(@PathVariable String code) {
    return CouponResponse.from(coupons.deactivate(code));
  }
}
