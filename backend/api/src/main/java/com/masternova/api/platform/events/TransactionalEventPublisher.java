package com.masternova.api.platform.events;

import com.masternova.api.platform.EventPublisher;
import com.masternova.kernel.event.DomainEvent;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes domain events to in-process observers through Spring's event bus.
 *
 * <p>Observers choose their timing:
 *
 * <ul>
 *   <li>{@code @TransactionalEventListener} (default phase AFTER_COMMIT) — runs only if the
 *       transaction commits. For reactions that must not see rolled-back changes (evict a cache).
 *   <li>{@code @EventListener} — runs immediately, inside the transaction. Rarely what you want.
 * </ul>
 *
 * <p>In-process observers are lost if the process dies right after commit — anything that MUST
 * happen goes through the outbox (Phase 2.4).
 */
@Component
@DesignPattern(value = Pattern.OBSERVER, role = "Subject", note = "patterns/docs/07-observer.md")
class TransactionalEventPublisher implements EventPublisher {

  private final ApplicationEventPublisher springEvents;

  TransactionalEventPublisher(ApplicationEventPublisher springEvents) {
    this.springEvents = springEvents;
  }

  // ⭐ MANDATORY: join the caller's transaction, or fail with IllegalTransactionStateException.
  //    Publishing outside a transaction would decouple the event from the change it describes.
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(DomainEvent event) {
    Objects.requireNonNull(event, "event");
    springEvents.publishEvent(event);
  }
}
