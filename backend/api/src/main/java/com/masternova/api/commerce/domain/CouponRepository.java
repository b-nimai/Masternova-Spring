package com.masternova.api.commerce.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Coupons, keyed by their normalized code. A Spring Data interface living directly in {@code
 * domain/}, like catalog's (commerce LLD §4).
 */
public interface CouponRepository extends JpaRepository<Coupon, String> {

  List<Coupon> findAllByOrderByCreatedAtDesc();

  /**
   * Counts one redemption, in the PAID transition's transaction. ⭐ An increment in SQL, never a
   * read-modify-write of the entity: two orders paid at once both count. The limit is checked at
   * quote time, so it is a SOFT limit (LLD §8).
   */
  @Modifying
  @Query(
      value = "UPDATE coupon SET redemptions = redemptions + 1 WHERE code = :code",
      nativeQuery = true)
  int redeem(String code);
}
