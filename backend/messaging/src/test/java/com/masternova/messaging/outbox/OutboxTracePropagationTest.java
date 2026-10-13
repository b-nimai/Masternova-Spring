package com.masternova.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.kernel.event.DomainEvent;
import com.masternova.messaging.OutboxHandler;
import com.masternova.messaging.OutboxMessage;
import com.masternova.messaging.OutboxProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler.FirstMatchingCompositeObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * ⭐ api request → outbox row → worker delivery is ONE trace (ADR-0015). No Spring, no database: a
 * fake repository stands in for the table, the OpenTelemetry SDK is real, its spans land in memory.
 * The registry is wired the way Boot wires it when tracing is on.
 */
class OutboxTracePropagationTest {

  static final String TYPE = "test.thing-happened.v1";

  record ThingHappened(String aggregateId) implements DomainEvent {
    @Override
    public String type() {
      return TYPE;
    }
  }

  /** The table, in memory: what was appended is what gets claimed. */
  static final class InMemoryOutbox implements OutboxRepository {
    final List<NewMessage> rows = new ArrayList<>();

    @Override
    public void append(NewMessage m) {
      rows.add(m);
    }

    @Override
    public List<OutboxMessage> claimDue(int batchSize, Duration lease) {
      List<OutboxMessage> due =
          rows.stream()
              .map(
                  m ->
                      new OutboxMessage(
                          m.id(),
                          m.type(),
                          m.aggregateId(),
                          m.payloadJson(),
                          m.occurredAt(),
                          1,
                          m.headers()))
              .toList();
      rows.clear();
      return due;
    }

    @Override
    public void markDone(UUID id) {}

    @Override
    public void reschedule(UUID id, Instant nextAttemptAt, String error) {}

    @Override
    public void markDead(UUID id, String error) {}

    @Override
    public Map<String, Long> countByStatus() {
      return Map.of();
    }

    @Override
    public Backlog backlog() {
      return new Backlog(rows.size(), null);
    }
  }

  final InMemorySpanExporter spans = InMemorySpanExporter.create();
  final ObservationRegistry observations = tracingRegistry(spans);
  final InMemoryOutbox outbox = new InMemoryOutbox();
  final Clock clock = Clock.systemUTC();
  final OutboxProperties settings =
      new OutboxProperties(
          true,
          Duration.ofSeconds(1),
          10,
          Duration.ofSeconds(60),
          3,
          Duration.ofSeconds(1),
          Duration.ofHours(1));

  static ObservationRegistry tracingRegistry(InMemorySpanExporter exporter) {
    var otel =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    var otelTracer = otel.getTracer("test");
    var tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {});
    var propagator = new OtelPropagator(otel.getPropagators(), otelTracer);
    ObservationRegistry registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(
            new FirstMatchingCompositeObservationHandler(
                new PropagatingSenderTracingObservationHandler<>(tracer, propagator),
                new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                new DefaultTracingObservationHandler(tracer)));
    return registry;
  }

  JdbcOutboxWriter writer(ObservationRegistry registry) {
    return new JdbcOutboxWriter(
        outbox, new JsonMapper(), clock, new SimpleMeterRegistry(), registry);
  }

  OutboxRelay relay(OutboxHandler handler) {
    return new OutboxRelay(
        outbox, List.of(handler), settings, clock, new SimpleMeterRegistry(), observations);
  }

  static OutboxHandler handler(Runnable onHandle) {
    return new OutboxHandler() {
      @Override
      public String eventType() {
        return TYPE;
      }

      @Override
      public void handle(OutboxMessage message) {
        onHandle.run();
      }
    };
  }

  SpanData span(String name) {
    return spans.getFinishedSpanItems().stream()
        .filter(s -> s.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no span " + name + " in " + spans));
  }

  @Test
  void theDeliveryContinuesTheTraceOfTheRequestThatWroteTheEvent() {
    // the api: an HTTP request's span is current while the service appends the event
    Observation.createNotStarted("http.server.requests", observations)
        .contextualName("POST /api/v1/things")
        .observe(() -> writer(observations).append(new ThingHappened("t1")));

    // the row carries a W3C traceparent: 00-<trace id>-<span id>-<flags (sampled …)>
    assertThat(outbox.rows)
        .singleElement()
        .satisfies(
            row ->
                assertThat(row.headers().get("traceparent"))
                    .matches("00-\\p{XDigit}{32}-\\p{XDigit}{16}-\\p{XDigit}{2}"));

    // the worker, later, on another thread: no span is current when the relay runs
    relay(handler(() -> {})).relayOnce();

    SpanData request = span("POST /api/v1/things");
    SpanData publish = span("outbox publish " + TYPE);
    SpanData deliver = span("outbox deliver " + TYPE);
    assertThat(publish.getKind()).isEqualTo(SpanKind.PRODUCER);
    assertThat(publish.getParentSpanId()).isEqualTo(request.getSpanId());
    assertThat(deliver.getKind()).isEqualTo(SpanKind.CONSUMER);
    assertThat(deliver.getTraceId()).isEqualTo(request.getTraceId()); // ⭐ one trace
    assertThat(deliver.getParentSpanId()).isEqualTo(publish.getSpanId());
  }

  @Test
  void aFailingHandlerMarksTheDeliverySpanAsAnError() {
    writer(observations).append(new ThingHappened("t2"));

    relay(
            handler(
                () -> {
                  throw new IllegalStateException("smtp down");
                }))
        .relayOnce();

    assertThat(span("outbox deliver " + TYPE).getStatus().getStatusCode())
        .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
  }

  @Test
  void withTracingOffTheHeadersStayEmptyAndDeliveryStillWorks() {
    writer(ObservationRegistry.NOOP).append(new ThingHappened("t3"));
    assertThat(outbox.rows.getFirst().headers()).isEmpty();

    List<String> handled = new ArrayList<>();
    var result = relay(handler(() -> handled.add("t3"))).relayOnce();

    assertThat(result.delivered()).isEqualTo(1);
    assertThat(handled).containsExactly("t3");
    // a fresh trace starts in the worker — nothing to continue
    assertThat(span("outbox deliver " + TYPE).getParentSpanContext().isValid()).isFalse();
  }
}
