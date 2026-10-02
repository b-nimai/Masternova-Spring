package com.masternova.api.platform.events;

import com.masternova.api.platform.EventPublisher;
import com.masternova.api.platform.outbox.OutboxWriter;
import com.masternova.kernel.event.DomainEvent;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a domain event two ways, both inside the CALLER's transaction:
 *
 * <ol>
 *   <li>⭐ durably — appended to the transactional outbox (committed with the business change,
 *       delivered later by the relay to {@code OutboxHandler}s; survives crashes);
 *   <li>in-process — through Spring's event bus to {@code @TransactionalEventListener} observers
 *       (cheap, immediate, but lost if the process dies right after commit).
 * </ol>
 *
 * <p>Observers choose their timing: {@code @TransactionalEventListener} (AFTER_COMMIT) never sees
 * rolled-back changes; {@code @EventListener} runs immediately inside the transaction.
 */
@Component
@DesignPattern(value = Pattern.OBSERVER, role = "Subject", note = "patterns/docs/07-observer.md")
class TransactionalEventPublisher implements EventPublisher {

  private final ApplicationEventPublisher springEvents;
  private final OutboxWriter outbox;

  TransactionalEventPublisher(ApplicationEventPublisher springEvents, OutboxWriter outbox) {
    this.springEvents = springEvents;
    this.outbox = outbox;
  }

  // ⭐ MANDATORY: join the caller's transaction, or fail with IllegalTransactionStateException.
  //    That is what makes the outbox row and the business change commit — or roll back — together.
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(DomainEvent event) {
    Objects.requireNonNull(event, "event");
    outbox.append(event);
    springEvents.publishEvent(event);
  }
}
