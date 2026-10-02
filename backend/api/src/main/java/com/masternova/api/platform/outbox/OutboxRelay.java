package com.masternova.api.platform.outbox;

import com.masternova.api.platform.MasternovaProperties;
import com.masternova.api.platform.OutboxHandler;
import com.masternova.api.platform.OutboxMessage;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delivers outbox messages to their {@link OutboxHandler}s: claim a batch → dispatch each message →
 * record the outcome. AT-LEAST-ONCE: a message is only marked DONE after its handler returned.
 *
 * <p>Deliberately NOT @Transactional: handlers may be slow (email, HTTP). Each repository call is
 * its own short statement, so no database connection is held while a handler runs.
 */
@Component
@DesignPattern(
    value = Pattern.TRANSACTIONAL_OUTBOX,
    role = "Relay",
    note = "patterns/docs/17-transactional-outbox.md")
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ERROR_LENGTH = 1_000;

  /** What one batch did — returned for tests and, later, metrics. */
  public record BatchResult(int delivered, int retried, int dead, int unhandled) {
    public int total() {
      return delivered + retried + dead + unhandled;
    }
  }

  private final OutboxRepository repository;
  private final Map<String, OutboxHandler> handlersByType;
  private final MasternovaProperties.Outbox settings;
  private final Clock clock;

  OutboxRelay(
      OutboxRepository repository,
      List<OutboxHandler> handlers,
      MasternovaProperties properties,
      Clock clock) {
    this.repository = repository;
    this.handlersByType = index(handlers);
    this.settings = properties.outbox();
    this.clock = clock;
  }

  /**
   * ⭐ A registry built ONCE from every OutboxHandler bean (note 04 Registry, note 08 List
   * injection).
   */
  private static Map<String, OutboxHandler> index(List<OutboxHandler> handlers) {
    Map<String, OutboxHandler> byType = new LinkedHashMap<>();
    for (OutboxHandler handler : handlers) {
      OutboxHandler previous = byType.putIfAbsent(handler.eventType(), handler);
      if (previous != null) {
        // fail at STARTUP: two consumers of one type in the same process is a wiring bug
        throw new IllegalStateException("two OutboxHandlers for " + handler.eventType());
      }
    }
    return Map.copyOf(byType);
  }

  public BatchResult relayOnce() {
    int delivered = 0;
    int retried = 0;
    int dead = 0;
    int unhandled = 0;
    for (OutboxMessage message : repository.claimDue(settings.batchSize(), settings.lease())) {
      OutboxHandler handler = handlersByType.get(message.type());
      if (handler == null) {
        // no consumer in this process (yet) — nothing to deliver; done, not failed
        repository.markDone(message.id());
        unhandled++;
        continue;
      }
      try {
        handler.handle(message);
        repository.markDone(message.id());
        delivered++;
      } catch (RuntimeException e) {
        String error = describe(e);
        if (message.attempts() >= settings.maxAttempts()) {
          repository.markDead(message.id(), error);
          log.error(
              "Outbox message {} ({}) is DEAD after {} attempts: {}",
              message.id(),
              message.type(),
              message.attempts(),
              error);
          dead++;
        } else {
          Duration delay = backoff(message.attempts());
          repository.reschedule(message.id(), clock.instant().plus(delay), error);
          log.warn(
              "Outbox message {} ({}) failed attempt {}; retrying in {}",
              message.id(),
              message.type(),
              message.attempts(),
              delay);
          retried++;
        }
      }
    }
    return new BatchResult(delivered, retried, dead, unhandled);
  }

  /** ⭐ Exponential backoff: base × 2^(attempt−1), capped — 1s, 2s, 4s, 8s … up to maxBackoff. */
  Duration backoff(int attempts) {
    int exponent = Math.min(Math.max(attempts - 1, 0), 30); // cap the shift: no overflow
    Duration delay = settings.baseBackoff().multipliedBy(1L << exponent);
    return delay.compareTo(settings.maxBackoff()) > 0 ? settings.maxBackoff() : delay;
  }

  private static String describe(RuntimeException e) {
    String text = e.getClass().getSimpleName() + ": " + e.getMessage();
    return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
  }
}
