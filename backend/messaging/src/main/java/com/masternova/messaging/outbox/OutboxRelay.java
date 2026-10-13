package com.masternova.messaging.outbox;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.messaging.OutboxProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers outbox messages to their {@link OutboxHandler}s: claim a batch → dispatch each message →
 * record the outcome. AT-LEAST-ONCE: a message is only marked DONE after its handler returned.
 *
 * <p>Deliberately NOT @Transactional: handlers may be slow (email, HTTP). Each repository call is
 * its own short statement, so no database connection is held while a handler runs.
 *
 * <p>Each delivery is a CONSUMER observation whose parent is read from the message's headers: the
 * worker's span joins the trace of the api request that wrote the event (ADR-0015).
 */
@DesignPattern(
    value = Pattern.TRANSACTIONAL_OUTBOX,
    role = "Relay",
    note = "patterns/docs/17-transactional-outbox.md")
@DesignPattern(
    value = Pattern.FACTORY_METHOD,
    role = "Registry",
    note = "patterns/docs/09-factory-method-registry.md")
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ERROR_LENGTH = 1_000;

  /** What one batch did — returned for tests; the same numbers go to {@code outbox.messages}. */
  public record BatchResult(int delivered, int retried, int dead, int unhandled) {
    public int total() {
      return delivered + retried + dead + unhandled;
    }
  }

  private final OutboxRepository repository;
  private final Map<String, OutboxHandler> handlersByType;
  private final OutboxProperties settings;
  private final Clock clock;
  private final MeterRegistry meters;
  private final ObservationRegistry observations;

  OutboxRelay(
      OutboxRepository repository,
      List<OutboxHandler> handlers,
      OutboxProperties settings,
      Clock clock,
      MeterRegistry meters,
      ObservationRegistry observations) {
    this.repository = repository;
    this.handlersByType = index(handlers);
    this.settings = settings;
    this.clock = clock;
    this.meters = meters;
    this.observations = observations;
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
        count(message, "unhandled");
        unhandled++;
        continue;
      }
      try {
        deliver(handler, message);
        repository.markDone(message.id());
        count(message, "delivered");
        recordLag(message);
        delivered++;
      } catch (RuntimeException e) {
        String error = describe(e);
        if (message.attempts() >= settings.maxAttempts()) {
          repository.markDead(message.id(), error);
          count(message, "dead");
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
          count(message, "retried");
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

  /** The handler call as a CONSUMER observation continuing the writer's trace. */
  private void deliver(OutboxHandler handler, OutboxMessage message) {
    ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get, Kind.CONSUMER);
    context.setCarrier(message.headers());
    context.setRemoteServiceName("outbox");
    Observation.createNotStarted("outbox.deliver", () -> context, observations)
        .contextualName("outbox deliver " + message.type())
        .lowCardinalityKeyValue("event.type", message.type())
        .highCardinalityKeyValue("outbox.message.id", message.id().toString())
        .highCardinalityKeyValue("outbox.attempt", Integer.toString(message.attempts()))
        .observe(() -> handler.handle(message));
  }

  // ⭐ Tags are the event TYPE (a closed set, ~10) and the outcome (4) — never an id: every distinct
  //    tag value is a new time series in Prometheus, kept for weeks (cardinality, devops note 05).
  private void count(OutboxMessage message, String outcome) {
    Counter.builder("outbox.messages")
        .description("Outbox messages the relay finished an attempt on, by outcome")
        .tag("event.type", message.type())
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }

  /**
   * End-to-end lag: the business change committed → its handler finished. The SLO is on this; its
   * buckets are the app's choice ({@code management.metrics.distribution.*}).
   */
  private void recordLag(OutboxMessage message) {
    Timer.builder("outbox.delivery.lag")
        .description("Time from the event's commit to its successful delivery")
        .tag("event.type", message.type())
        .register(meters)
        .record(Duration.between(message.occurredAt(), clock.instant()));
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
