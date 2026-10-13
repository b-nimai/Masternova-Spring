# DevOps 05: Observability — metrics, traces, logs, and alerts that mean something

> **One-liner:** monitoring tells you *that* something is wrong; observability lets you ask *why*
> without shipping new code. Three signals, one correlation key (the **trace id**), and alerts
> written against **user-visible objectives**, not against CPU graphs.

**Roadmap:** D3.1–D3.5 · **Last updated:** 2026-10-13
**Real files:** [`deploy/observability/`](../../deploy/observability/) (Prometheus, Grafana provisioning + dashboards), `compose.yaml` (profile `observability`), [`backend/messaging/…/outbox/`](../../backend/messaging/src/main/java/com/masternova/messaging/outbox/) (outbox meters), [`JobRunner`](../../backend/worker/src/main/java/com/masternova/worker/pipeline/job/JobRunner.java) + [`JobPoller`](../../backend/worker/src/main/java/com/masternova/worker/pipeline/job/JobPoller.java) (pipeline meters), api + worker `application.yaml` (`management.*`)

**Priority marks:** ⭐⭐⭐ must know (interviews, incidents) · ⭐⭐ use daily · ⭐ good to know.

| # | Section | Priority | Task |
|---|---|---|---|
| 1 | [The three signals at a glance](#1-the-three-signals-at-a-glance-) | ⭐⭐⭐ | — |
| 2 | [Metrics: Micrometer → Prometheus](#2-metrics-micrometer--prometheus-) | ⭐⭐⭐ | D3.1 |
| 3 | [Dashboards as code](#3-dashboards-as-code-) | ⭐⭐ | D3.2 |
| 4 | [Distributed tracing, through the outbox](#4-distributed-tracing-through-the-outbox-) | ⭐⭐⭐ | D3.3 |
| 5 | [Centralized logs, and the jump between log and trace](#5-centralized-logs-and-the-jump-between-log-and-trace-) | ⭐⭐ | D3.4 |

---

## 1. The three signals at a glance ⭐⭐⭐

| Signal | Answers | Cost driver | Here |
|---|---|---|---|
| **Metrics** | *Is it healthy? How much, how fast, how often?* Cheap aggregates over time | number of **series** (cardinality) | Micrometer → Prometheus |
| **Traces** | *Where did this one request spend its time, across processes?* | number of **spans** (sampling) | Micrometer Tracing → OTLP → Tempo |
| **Logs** | *What exactly happened, with which values?* | **bytes** | JSON lines → Alloy → Loki |

The rule that ties them together: **every log line carries the trace id**, every trace links to
its logs, and dashboards link to both. An alert fires on a metric; you open the trace of a slow
or failed request; the trace shows the logs of every process it crossed.

```mermaid
flowchart LR
  subgraph apps[api + worker]
    M[Micrometer meters] --> P["/actuator/prometheus"]
    O[Observations] --> M
    O --> T[spans]
    L[logs with traceId]
  end
  P -- scrape 15 s --> Prom[(Prometheus)]
  T -- OTLP push --> Tempo[(Tempo)]
  L -- stdout → Alloy --> Loki[(Loki)]
  Prom --> G[Grafana]
  Tempo --> G
  Loki --> G
  Prom -- rules --> AM[Alertmanager] --> Mail[Mailpit]
```

---

## 2. Metrics: Micrometer → Prometheus ⭐⭐⭐

### 2.1 Pull, not push

Each app serves its current meter values at **`/actuator/prometheus`** (Prometheus text format,
from `micrometer-registry-prometheus`); Prometheus **scrapes** every 15 s. Pull means a dead app
is *noticed* (`up == 0`) instead of silently sending nothing, and the app needs no idea where
monitoring lives.

The scrape endpoint is public on the app's port (`PlatformConfig.platformPublicEndpoints`) but
users can't reach it: nginx — and later the ingress — only forwards `/api/`. In a bigger setup the
alternative is `management.server.port` on a separate, unrouted port.

### 2.2 What to measure: RED and USE

| Method | For | Metrics | Here |
|---|---|---|---|
| **RED** — Rate, Errors, Duration | anything that *serves requests* | requests/s, failed/s, latency histogram | `http_server_requests_seconds_*` (Boot, automatic), `pipeline_job_seconds_*`, `outbox_messages_total` |
| **USE** — Utilisation, Saturation, Errors | anything that is a *resource* | busy/total, queue length, error count | `hikaricp_connections_active/pending`, `jvm_*`, `pipeline_slots_busy` / `pipeline_slots`, `pipeline_jobs_queued` |

The custom meters added in D3.1, and the question each answers:

| Meter (Prometheus name) | Type | Tags | Answers | Where |
|---|---|---|---|---|
| `outbox_events_written_total` | counter | `event_type` | how many business facts happen (registrations, uploads — orders from Phase 9 with no new code) | `JdbcOutboxWriter` |
| `outbox_messages_total` | counter | `event_type`, `outcome` = delivered / retried / dead / unhandled | is delivery failing? | `OutboxRelay` |
| `outbox_delivery_lag_seconds` | histogram | `event_type` | commit → handled: the **outbox lag** (an SLO, D3.5) | `OutboxRelay` |
| `outbox_backlog`, `outbox_oldest_age_seconds` | gauges | — | is the relay keeping up — or *gone*? | `OutboxBacklogMetrics` |
| `pipeline_job_seconds` | histogram (an Observation) | `job_type`, `outcome`, `error` | **transcode duration** by step, and how runs end | `JobRunner` |
| `pipeline_slots_busy`, `pipeline_slots`, `pipeline_jobs_queued` | gauges | — | saturation: is it time to add workers? | `JobPoller` |

### 2.3 Cardinality: the one way to break Prometheus ⭐⭐⭐

Every distinct combination of tag values is a separate **time series**, stored for weeks. Tags
must come from a **small, closed set**:

- ✅ `event_type` (~10), `outcome` (4–5), `job_type` (4), `status` (a few), `uri` **template**
  (`/api/v1/courses/{slug}`, which Boot records, never the raw path).
- ❌ user id, asset id, course slug, email, the raw URL, an exception *message*. 10⁵ users ×
  20 buckets = 2 million series from one meter.

Ids still matter for debugging — they go on **spans** (and logs), not meters. The Observation API
makes the split explicit: `lowCardinalityKeyValue("job.type", …)` becomes a metric tag *and* a
span attribute; `highCardinalityKeyValue("asset.id", …)` goes to the span only (`JobRunner`).

### 2.4 Histograms, not client-side percentiles ⭐⭐⭐

A p95 computed inside each replica **cannot be averaged** across replicas (the mean of two p95s is
not a p95). A histogram exports **bucket counters** (`_bucket{le="0.3"}`); Prometheus adds them
up across replicas and computes any quantile afterwards:

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="api"}[5m])))
```

Buckets cost series too. Micrometer's full histogram is **~54 buckets** per series between 5 ms
and 10 s (measured in `PrometheusScrapeIT` while building D3.1), times every `uri × method ×
status`. So HTTP gets **9 chosen edges** (`slo: 25ms … 5s`), including the exact **300 ms** edge
the latency SLO counts against: `le="0.3"` is a real bucket, not an interpolation. The pipeline
keeps the full histogram (`percentiles-histogram`) because it has ~20 series in total, and its
maximum is raised to 2 h (the 30 s default would put every transcode in `+Inf`).

### 2.5 Metrics that tell the truth

- **Count on commit.** `outbox_events_written_total` increments in an `afterCommit` callback:
  a rolled-back registration wrote no row, so it must not show up as a registration either
  (`TransactionalOutboxIT.onlyCommittedEventsAreCounted`).
- **Gauges for silent failure.** A stopped relay records *nothing* — its counters just flatline,
  which looks like a quiet night. `outbox_oldest_age_seconds` is read from the table at scrape
  time, so it keeps growing when nobody delivers. Its query uses the partial due-index, so it
  stays cheap at any table size (a `count(*)` over all DONE rows would not).
- **Observations over Timers** for anything with a duration: `Observation.start/stop` gives a
  timer today and, with a tracer on the classpath (D3.3), a span from the same code.
- **Library meters, app decisions.** The messaging library registers meters but not their buckets:
  `management.metrics.distribution.*` is set per app (the worker publishes the outbox-lag
  histogram with an exact 60 s edge). Without a `MeterRegistry` bean the library falls back to
  Micrometer's global registry, which drops everything — metrics are never needed to run.
- **One `application` tag** (`management.metrics.tags.application`) on every series tells api
  from worker; Prometheus adds `instance` for the replica.

**Try it:** `make api`, then `curl -s localhost:8080/actuator/prometheus | grep -E '^(http_server|hikaricp|outbox)'`.

---

## 3. Dashboards as code ⭐⭐

**Try it:** `make stack` (or `make api` + `make worker`), then `make obs` →
<http://localhost:3001> (Grafana, anonymous viewer) · <http://localhost:9091> (Prometheus).

### 3.1 Nothing is clicked together

| File | What Grafana does with it |
|---|---|
| `grafana/provisioning/datasources/datasources.yaml` | creates the Prometheus datasource with a **fixed `uid: prometheus`** at startup |
| `grafana/provisioning/dashboards/dashboards.yaml` | loads every JSON in `grafana/dashboards/` into the *Masternova* folder, re-reads every 30 s, `allowUiUpdates: false` |
| `grafana/dashboards/*.json` | the four dashboards below |

Grafana has **no state volume**: delete the container and nothing is lost, because everything it
shows comes from git. A change made in the UI can't be saved over a provisioned dashboard; you
export the JSON, commit it, and review it like code. The fixed datasource `uid` keeps dashboards
portable: a Grafana in Kubernetes (D4) that provisions the same uid renders the same files.

| Dashboard | Method | Panels worth knowing |
|---|---|---|
| **HTTP (RED)** | RED | 5xx ratio and *share under 300 ms* (the two SLIs of §6), p50/p95/p99 from buckets, slowest routes table |
| **JVM** | USE | heap used vs max, *live data after GC* (the leak signal), GC pause share, threads by state, CPU |
| **Connection pool (Hikari)** | USE | in use / max, **threads waiting** (with virtual threads this is the queue that matters), acquire and hold times, timeouts |
| **Pipeline & outbox** | custom | oldest undelivered event, backlog, commit → delivery quantiles, job duration p95 by step, outcomes, slots and queue |

### 3.2 Details that make a dashboard honest

- **Rates use `$__rate_interval`**, never a fixed `[1m]`: Grafana makes it ≥ 4 scrape intervals
  (the datasource declares `timeInterval: 15s`), so a zoomed-in panel never computes a rate
  from a single sample.
- **The HTTP dashboard excludes `/actuator/**` and the SSE streams** (`…/stream`): Prometheus'
  own scrapes would dominate the rate, and a stream is *meant* to stay open for minutes; either one
  would wreck the latency quantiles.
- **A ratio with nothing in the numerator is 0, not "No data":** `(sum(5xx) or vector(0)) /
  sum(all)`.
- **Gauges every worker reports alike use `max`, not `sum`** (`pipeline_jobs_queued` is one
  table count seen by N workers); per-worker gauges (`pipeline_slots_busy`) use `sum`.

### 3.3 One scrape target per app — found while building it

The first config listed each app twice: `api:8080` (the container) and
`host.docker.internal:8080` (for `make api` from source), expecting one of the two to be down. In
stack mode **both answered**: the host port *is* the container's published port. Every series
existed twice and every `sum()` doubled. Now each app has one target, through the host, which
reaches either mode. The lesson: the same process scraped twice looks like two replicas, so
check `count by (job) (up)` when you add a target.

Every panel's query was run against the live stack with real traffic before commit; the only empty
ones are for events that hadn't happened (5xx, failed jobs).

---

## 4. Distributed tracing, through the outbox ⭐⭐⭐

Decision record: [ADR-0015](../../docs/adr/0015-tracing-with-micrometer-observation-and-otlp.md).

### 4.1 Vocabulary

- **Trace:** one unit of work end to end, identified by a 128-bit **trace id**.
- **Span:** one timed step in it (an HTTP request, a publish, a delivery). It has a span id, a
  parent span id, a **kind** (SERVER, CLIENT, PRODUCER, CONSUMER, INTERNAL), attributes and a
  status.
- **Context propagation:** carrying `(trace id, parent span id, sampled flag)` across a process
  boundary. The W3C format is one header,
  `traceparent: 00-<trace id>-<span id>-<flags>`.
- **Sampling:** deciding which traces to keep. **Parent-based**: the root decides, and every
  downstream hop obeys the flag, so a trace is never half-kept.

### 4.2 The path here

```mermaid
sequenceDiagram
  participant B as Browser
  participant A as api
  participant DB as outbox_message
  participant W as worker (relay)
  participant T as Tempo
  B->>A: POST /api/v1/auth/signup
  Note over A: SERVER span (trace T1)
  A->>DB: INSERT … headers = {traceparent: 00-T1-P-03}
  Note over A: PRODUCER span "outbox publish …" (parent: SERVER)
  A-->>T: OTLP (batched, async)
  W->>DB: claim (FOR UPDATE SKIP LOCKED)
  Note over W: CONSUMER span "outbox deliver …" parent = P, from the row
  W-->>T: OTLP
```

Verified on the running stack: one `POST /api/v1/auth/signup` gave one trace in Tempo with 8
spans from **two services**: the api's SERVER span, Spring Security's filter-chain spans, the
Modulith span `Identity > AuthController.signup`, the PRODUCER `outbox publish
identity.user-registered.v1`, and the worker's CONSUMER `outbox deliver
identity.user-registered.v1`.

### 4.3 Why the table needs headers

HTTP clients and message brokers carry `traceparent` for you. An outbox has no wire: the api
**writes a row** and a different process **polls** for it later. So the context has to be stored
with the event. The writer and relay use the Observation API's transport contexts:

| Side | Context | Carrier | What Boot's handler does |
|---|---|---|---|
| `JdbcOutboxWriter` | `SenderContext<Map>` (PRODUCER) | a `HashMap`, then saved as `headers` JSONB | **injects** the producer span's `traceparent` |
| `OutboxRelay` | `ReceiverContext<Map>` (CONSUMER) | `message.headers()` | **extracts** it and makes the delivery span a child |

The messaging library depends only on `micrometer-observation`, not on OpenTelemetry. Without a
tracer the observations are no-ops and `headers` stays empty
(`OutboxTracePropagationTest.withTracingOffTheHeadersStayEmptyAndDeliveryStillWorks`).

### 4.4 Settings, and the traps found

| Setting | Why |
|---|---|
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` as an **env var** (compose), not a YAML default | the exporter switches on when the property merely *exists*; a `${X:}` empty default would create one with no endpoint |
| `management.tracing.sampling.probability: ${TRACING_SAMPLING_PROBABILITY:1.0}` | everything locally; ~0.1 in production. Boot's own default, 0.1, would drop 9 of 10 local requests |
| `management.observations.enable.tasks.scheduled.execution: false` | Spring observes every `@Scheduled` run: a root trace per relay poll (1 s) and job poll (2 s) |
| the starter's `micrometer-registry-otlp` excluded | Prometheus already scrapes the metrics; pushing them over OTLP too would be a second, unused path |
| Tempo has no compose healthcheck | the image is distroless: no shell, no `wget`. Grafana waits for `service_started` and retries |

### 4.5 Trace id in every log line

With a tracer present, Boot puts `traceId` and `spanId` in the logging MDC. The containers log
**ECS JSON** (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), and Boot's ECS writer emits them as
**top-level `traceId` / `spanId` fields**, *not* ECS's nested `trace.id` (found by
`TraceCorrelationIT`, which first looked for the nested one). That IT signs up, finds the JSON log
line written during the request, and checks that its `traceId` is the trace id in the outbox
row's `traceparent`: one id from the log line to the worker. From source, the console pattern
shows the same ids.

**Try it:** `make stack && make obs`, sign up, then open Grafana → Explore → Tempo → *Search*,
service `api`: the trace shows both services.

---

## 5. Centralized logs, and the jump between log and trace ⭐⭐

### 5.1 The pipeline

```mermaid
flowchart LR
  A[api / worker<br/>ECS JSON on stdout] --> D[Docker json-file log]
  D -- Docker API --> Al[Alloy<br/>discovery.docker → loki.process]
  Al -- push --> L[(Loki)]
  L --> G[Grafana]
```

The apps only **write lines to stdout** (twelve-factor); they know nothing about Loki. Collecting
logs is the platform's job: here **Grafana Alloy** discovers this compose project's containers
through the Docker socket (`deploy/observability/alloy/config.alloy`), and on Kubernetes (D4) it
runs as a DaemonSet reading `/var/log/pods`. The apps don't change between the two.

### 5.2 Labels vs structured metadata: cardinality again ⭐⭐⭐

Loki indexes **labels only**; each distinct label set is a *stream*. So:

| Field | Stored as | Why |
|---|---|---|
| `service` (compose service) | **label** | ~10 values, and every query starts from it |
| `level` (from the JSON's `log.level`) | **label** | 5 values; "errors of the worker" is a common query |
| `traceId`, `spanId` | **structured metadata** | one value per request. As labels they would make a stream per request, which is Loki's cardinality explosion |
| everything else (message, logger, thread) | the line itself | searched with `\|=` / `\| json` at query time |

A trace's logs are then one query, even across services:

```logql
{service=~"api|worker"} | traceId="4ab014d438c35439a90683497e22a316"
```

### 5.3 Jumping between signals

| From → to | How (provisioned in `datasources.yaml`) |
|---|---|
| **log → trace** | Loki's `derivedFields`: the `traceId` metadata field (`matcherType: label`) becomes an *Open trace* link to the Tempo datasource |
| **trace → logs** | Tempo's `tracesToLogsV2` with a custom query: `{service="${__span.tags["service.name"]}"} \| traceId="${__trace.traceId}"`, ± 5 min around the span |

(`$$` in the provisioning file escapes Grafana's own `$ENV` interpolation, so the literal `${…}`
reaches the datasource.)

Verified on the running stack: a signup's trace id `4ab014d4…` opened 8 spans in Tempo (api +
worker), and the LogQL above returned the worker's `verify-email … sent` line from the same
trace. The api had no lines in it, because a successful signup logs nothing there.

### 5.4 Found while wiring it

- **The worker logged nothing when it sent an email.** An external side effect with no log line
  can only be traced through the database. `NotificationService` now logs
  `<template> for event <id> sent, provider id <…>`, with ids only (an email address in a log is
  personal data that spreads to every log store).
- **Loki's and Tempo's images are distroless and Alloy has no `wget`**, so none of them has a
  compose healthcheck. Grafana waits for `service_started` and its datasources retry.
- **Alloy reads the Docker socket**: read access to the Docker API is root-equivalent on the host.
  That's acceptable on a laptop but not on a shared machine; the Kubernetes DaemonSet reads files
  instead.
- Lines that aren't JSON (the JVM's own startup warnings) still arrive, without a `level`
  label: the `stage.match` only parses the Spring apps' streams.

**Try it:** `make stack && make obs` → <http://localhost:3001> → Explore → Loki →
`{service="worker"}`, open a line, click **Open trace**. Alloy's pipeline graph is at
<http://localhost:12345>.
