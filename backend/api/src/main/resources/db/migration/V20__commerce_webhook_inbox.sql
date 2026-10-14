-- Commerce (Phase 9.6): the webhook inbox — one row per provider EVENT we have processed.
-- ADR-0018: claimed (INSERT … ON CONFLICT DO NOTHING) in the same transaction as the event's
-- effects, so a row exists only for an event whose effects committed.
CREATE TABLE payment_webhook_event (
    provider          VARCHAR(20)  NOT NULL,
    event_id          VARCHAR(100) NOT NULL,       -- X-Razorpay-Event-Id, or sha256:<body>
    event_type        VARCHAR(60)  NOT NULL,
    provider_order_id VARCHAR(100),
    outcome           VARCHAR(20),                 -- set at the end of the same transaction
    received_at       TIMESTAMPTZ  NOT NULL,
    -- ⭐ THE dedupe: a second copy's insert waits on the first's uncommitted key, then conflicts
    PRIMARY KEY (provider, event_id),
    CONSTRAINT payment_webhook_event_outcome_ck
        CHECK (outcome IN ('PROCESSED', 'IGNORED', 'UNKNOWN_ORDER'))
);

-- support: "did we get the webhooks for this order?"
CREATE INDEX payment_webhook_event_order_idx ON payment_webhook_event (provider_order_id);
