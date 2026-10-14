package com.masternova.messaging.outbox;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.messaging.OutboxProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
  private final ObservationRegistry observations;
  private final Meter.MeterProvider<Counter> messages;
  private final Meter.MeterProvider<Timer> lag;

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
    this.observations = observations;
    // ⭐ Tags are the event TYPE (a closed set, ~10) and the outcome (4) — never an id: every
    //    distinct tag value is a new time series in Prometheus, kept for weeks (devops note 05).
    this.messages =
        Counter.builder("outbox.messages")
            .description("Outbox messages the relay finished an attempt on, by outcome")
            .withRegistry(meters);
    // commit → handled; the SLO is on this, its buckets are the app's choice (management.metrics.*)
    this.lag =
        Timer.builder("outbox.delivery.lag")
            .description("Time from the event's commit to its successful delivery")
            .withRegistry(meters);
    // ⭐ Every handled type's counters exist at 0 from startup. Prometheus' increase() can't see a
    //    series that is BORN at 1 (no earlier sample), so the first DEAD message would never alert.
    for (String type : handlersByType.keySet()) {
      for (Outcome outcome : Outcome.values()) {
        counter(type, outcome);
      }
    }
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
    Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
    for (OutboxMessage message : repository.claimDue(settings.batchSize(), settings.lease())) {
      Outcome outcome = relay(message);
      counter(message.type(), outcome).increment();
      outcomes.merge(outcome, 1, Integer::sum);
    }
    return new BatchResult(
        outcomes.getOrDefault(Outcome.DELIVERED, 0),
        outcomes.getOrDefault(Outcome.RETRIED, 0),
        outcomes.getOrDefault(Outcome.DEAD, 0),
        outcomes.getOrDefault(Outcome.UNHANDLED, 0));
  }

  /** How one message's attempt ended — the {@code outcome} tag of {@code outbox.messages}. */
  enum Outcome {
    DELIVERED,
    RETRIED,
    DEAD,
    UNHANDLED; // no consumer in this process: done, not failed

    String tag() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private Outcome relay(OutboxMessage message) {
    OutboxHandler handler = handlersByType.get(message.type());
    if (handler == null) {
      repository.markDone(message.id());
      return Outcome.UNHANDLED;
    }
    return deliver(handler, message);
  }

  /**
   * One delivery attempt as a CONSUMER observation continuing the writer's trace. ⭐ The
   * observation's scope covers the outcome too — marking the row and the retry / DEAD log lines —
   * so those lines carry the trace id of the event they're about (the log → trace link matters most
   * for failures).
   */
  private Outcome deliver(OutboxHandler handler, OutboxMessage message) {
    ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get, Kind.CONSUMER);
    context.setCarrier(message.headers());
    context.setRemoteServiceName("outbox");
    Observation observation =
        Observation.createNotStarted("outbox.deliver", () -> context, observations)
            .contextualName("outbox deliver " + message.type())
            .lowCardinalityKeyValue("event.type", message.type())
            .highCardinalityKeyValue("outbox.message.id", message.id().toString())
            .highCardinalityKeyValue("outbox.attempt", Integer.toString(message.attempts()));
    return observation.observe(
        () -> {
          try {
            handler.handle(message);
            repository.markDone(message.id());
            lag.withTag("event.type", message.type())
                .record(Duration.between(message.occurredAt(), clock.instant()));
            return Outcome.DELIVERED;
          } catch (RuntimeException e) {
            observation.error(e);
            return failed(message, describe(e));
          }
        });
  }

  private Outcome failed(OutboxMessage message, String error) {
    if (message.attempts() >= settings.maxAttempts()) {
      repository.markDead(message.id(), error);
      log.error(
          "Outbox message {} ({}) is DEAD after {} attempts: {}",
          message.id(),
          message.type(),
          message.attempts(),
          error);
      return Outcome.DEAD;
    }
    Duration delay = backoff(message.attempts());
    repository.reschedule(message.id(), clock.instant().plus(delay), error);
    log.warn(
        "Outbox message {} ({}) failed attempt {}; retrying in {}",
        message.id(),
        message.type(),
        message.attempts(),
        delay);
    return Outcome.RETRIED;
  }

  private Counter counter(String type, Outcome outcome) {
    return messages.withTags("event.type", type, "outcome", outcome.tag());
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
