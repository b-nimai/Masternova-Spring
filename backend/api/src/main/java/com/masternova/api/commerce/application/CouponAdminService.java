package com.masternova.api.commerce.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.commerce.domain.Coupon;
import com.masternova.api.commerce.domain.CouponRepository;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.ValidationException;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin's coupon tools: create, list, switch off. Coupons are never deleted (orders cite them).
 */
@Service
public class CouponAdminService {

  /** ⭐ Coupons are in the catalog's one currency (V9), or a discount couldn't apply to anything. */
  public static final Currency CURRENCY = Currency.getInstance("INR");

  private final CouponRepository coupons;
  private final CatalogApi catalog;
  private final Clock clock;

  CouponAdminService(CouponRepository coupons, CatalogApi catalog, Clock clock) {
    this.coupons = coupons;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional
  public Coupon create(String code, Coupon.Terms terms) {
    terms
        .courseId()
        .filter(id -> !catalog.courseExists(id))
        .ifPresent(
            id -> {
              throw new NotFoundException("Course", id);
            });
    Coupon coupon;
    try {
      coupon = Coupon.create(code, terms, CURRENCY, clock.instant());
    } catch (IllegalArgumentException e) {
      throw ValidationException.of("coupon", "INVALID_COUPON", e.getMessage());
    }
    if (coupons.existsById(coupon.code())) {
      throw new ConflictException("COUPON_CODE_TAKEN", "A coupon with this code already exists.");
    }
    return coupons.save(coupon);
  }

  @Transactional(readOnly = true)
  public List<Coupon> list() {
    return coupons.findAllByOrderByCreatedAtDesc();
  }

  @Transactional
  public Coupon deactivate(String code) {
    Coupon coupon =
        coupons
            .findById(Coupon.normalize(code))
            .orElseThrow(() -> new NotFoundException("Coupon", code));
    coupon.deactivate();
    return coupon;
  }
}
