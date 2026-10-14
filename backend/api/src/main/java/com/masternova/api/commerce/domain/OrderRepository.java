package com.masternova.api.commerce.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * The Order aggregate's repository: a Spring Data interface in {@code domain/}, like catalog's.
 *
 * <p>⭐ Every change to an order starts with a {@code lock…} method: {@code SELECT … FOR UPDATE}
 * serialises the messengers racing for one order (the browser's confirm and the webhook, 50 copies
 * of one webhook). The loser waits a few milliseconds, then reads the winner's state and is
 * ignored.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PurchaseOrder o where o.id = :id")
  Optional<Order> lockById(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PurchaseOrder o where o.providerOrderId = :providerOrderId")
  Optional<Order> lockByProviderOrderId(String providerOrderId);

  /**
   * Whether the buyer holds this course through ANOTHER paid order — then a refund of this one must
   * not revoke it (two tabs can buy one course twice; "already owned" is checked at quote).
   */
  @Query(
      "select count(o) > 0 from PurchaseOrder o join o.lines l where o.buyerId = :buyerId"
          + " and l.courseId = :courseId and o.status = :paid and o.id <> :orderId")
  boolean paidElsewhere(UUID buyerId, UUID courseId, UUID orderId, OrderStatus paid);

  /**
   * ⭐ Claims a PAID order for a refund BEFORE the provider is called (one conditional UPDATE): a
   * second request finds it claimed and is refused, so money goes back once. A claim older than
   * {@code staleBefore} belongs to a crashed request and may be taken over.
   *
   * @return 1 if this request may refund, 0 if it isn't PAID or another refund is in flight
   */
  @Modifying
  @Query(
      value =
          "UPDATE purchase_order SET refund_requested_at = :now WHERE id = :id AND status = 'PAID'"
              + " AND (refund_requested_at IS NULL OR refund_requested_at < :staleBefore)",
      nativeQuery = true)
  int claimRefund(UUID id, Instant now, Instant staleBefore);

  /** The provider refused: let the next request try again. */
  @Modifying
  @Query(
      value = "UPDATE purchase_order SET refund_requested_at = NULL WHERE id = :id",
      nativeQuery = true)
  int releaseRefund(UUID id);

  /** A buyer's own order; someone else's is "not found" (API conventions §1). */
  Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);

  /** "My orders", newest first (index {@code purchase_order_user_idx}). */
  List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Limit limit);

  /** The admin's list, newest first. */
  List<Order> findAllByOrderByCreatedAtDesc(Limit limit);
}
