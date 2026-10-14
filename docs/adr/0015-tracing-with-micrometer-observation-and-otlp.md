# ADR-0015 — Tracing: Micrometer Observation over the OpenTelemetry SDK, trace context stored with each outbox event

**Status:** accepted · **Date:** 2026-10-13 · **Deciders:** Nimai
**Context links:** [DevOps note 05](../../patterns/devops/05-observability.md) §4 · [ADR-0008](0008-shared-messaging-module.md) (the outbox protocol) · [`docs/lld/platform-kernel.md`](../lld/platform-kernel.md) §9

## Context

A user signs up. The api writes the user and an `identity.user-registered.v1` row in one
transaction; seconds later the worker's relay claims the row and sends the welcome email. When
that email is slow or fails, the question is "what happened to *this* signup?", and the answer
spans two processes, a database table and a poll loop.

Two decisions:

1. **How are spans produced?** Options: the **OpenTelemetry Java agent** (bytecode
   instrumentation, attached with `-javaagent`), or **Micrometer Observation + Micrometer Tracing**
   with the OpenTelemetry SDK behind its bridge (Spring Boot's native path).
2. **How does a trace cross the outbox?** There is no HTTP call or message broker between the
   api and the worker to carry a `traceparent` header. The table is the transport.

## Decision

**1. Micrometer Observation, bridged to the OpenTelemetry SDK, exported over OTLP/HTTP to Tempo.**
`spring-boot-starter-opentelemetry` in both apps (its OTLP *metrics* registry excluded: Prometheus
scrapes the meters, D3.1). Our own instrumentation is written once against the Observation API
(`outbox.publish`, `outbox.deliver`, `pipeline.job`), and each observation gives a timer *and* a
span.

**2. The outbox row carries the trace context, in a `headers` JSONB column (`V16`).**

- `JdbcOutboxWriter.append` is a **PRODUCER** observation (`SenderContext`): Boot's propagating
  handler writes the new span's W3C `traceparent` into a map, and the map is stored with the event.
- `OutboxRelay` delivers inside a **CONSUMER** observation (`ReceiverContext`) whose carrier is
  that map: the worker's span becomes a child of the api's producer span. The result is one trace:
  `POST /api/v1/auth/signup` → `outbox publish …` → (worker) `outbox deliver …`.
- The column is a generic map, like Kafka or AMQP message headers: `tracestate` or baggage
  fit without another migration. Rows without it (older, or tracing off) deliver as before and
  start a fresh trace.
- The span per `@Scheduled` poll is switched off (`management.observations.enable.tasks.scheduled.execution: false`):
  a trace every second from the relay's idle polls would bury the real ones, and a delivery
  should hang under the request that caused it, not under the poll that found it.

## Alternatives rejected

| Alternative | Why not |
|---|---|
| **OpenTelemetry Java agent** | Broadest auto-instrumentation (JDBC, the AWS SDK, JavaMail) with no code. But it is a second instrumentation system beside Micrometer (two views of HTTP, metric names to reconcile), it adds startup time and a `-javaagent` to the Dockerfile and its AOT cache (devops note 02), and it can't see *our* semantics: it would never know that a row in `outbox_message` is a message. We still need the Observation API for the outbox, so the agent would be extra, not a replacement. Revisit if JDBC-level spans become necessary (then `datasource-micrometer` first). |
| **Dedicated `trace_id` / `span_id` columns** | Stores one propagation format's internals. W3C `traceparent` already encodes trace, parent and sampling flags; a map also takes `tracestate` and baggage. |
| **Span links instead of parent/child** | The OpenTelemetry messaging conventions allow either. A link fits batches that merge many producers into one consumer span; our relay delivers one message per span, so parent/child gives the simpler waterfall in Tempo and an exact "time in queue" gap. |
| **Spring Modulith's event publication registry** | We hand-rolled the outbox (ADR-0005), so the registry's own tracing integration doesn't apply. |
| **Zipkin / Jaeger as the backend** | OTLP is the vendor-neutral wire format; Tempo fits the Grafana stack (Prometheus, Loki) and its trace ↔ log ↔ metric links. Any OTLP backend can take its place by changing one endpoint. |

## Consequences

- **Positive:** one id from the browser's request to the worker's handler; the same id is on
  every log line (`traceId` in Boot's ECS JSON, D3.4), so a trace can open its logs and a log line
  its trace. Instrumenting new code means one Observation, which gives both a metric and a span.
- **Negative:** no automatic spans for JDBC, SMTP or S3 calls: inside the worker's
  `outbox deliver` span the time is one block. The outbox row grows by ~80 bytes.
- **Sampling** is decided at the root (parent-based) and travels in the `traceparent` flags, so a
  sampled request stays sampled across the outbox. Locally `TRACING_SAMPLING_PROBABILITY`
  defaults to 1.0; a real environment sets ~0.1.
- **Follow-up:** pipeline jobs start their own traces. Linking `MediaUploaded` → PROBE → … →
  READY would mean storing the same headers on `pipeline_job` rows.
