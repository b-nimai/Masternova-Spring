-- D3.3 — trace context travels WITH the event (ADR-0015): the writer stores the W3C `traceparent`
-- (and `tracestate`, baggage) of the request that caused the event; the relay continues that trace
-- in the worker. A map, like message headers in Kafka or AMQP, so other propagation fields fit
-- later without a migration. NULL for rows written before this column, or with tracing off.
ALTER TABLE outbox_message ADD COLUMN headers JSONB;
