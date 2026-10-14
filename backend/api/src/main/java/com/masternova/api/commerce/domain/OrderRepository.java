package com.masternova.api.commerce.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  /** A buyer's own order; someone else's is "not found" (API conventions §1). */
  Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);

  /** "My orders", newest first (index {@code purchase_order_user_idx}). */
  List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Limit limit);

  /** The admin's list, newest first. */
  List<Order> findAllByOrderByCreatedAtDesc(Limit limit);
}
