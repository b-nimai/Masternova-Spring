-- Transactional outbox (platform kernel, Phase 2.4) — docs/lld/platform-kernel.md §5, §9.
-- A row is written in the SAME transaction as the business change that caused it, then delivered
-- by the relay. Status only moves PENDING → PROCESSING → DONE | PENDING (retry) | DEAD.
CREATE TABLE outbox_message (
    id              UUID         PRIMARY KEY,
    event_type      VARCHAR(200) NOT NULL,                 -- DomainEvent.type(), e.g. identity.user-registered.v1
    aggregate_id    VARCHAR(200) NOT NULL,
    payload         JSONB        NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    -- when the row is next due. For PROCESSING rows it is the LEASE expiry: a relay that dies
    -- mid-batch leaves the row PROCESSING, and it simply becomes due again when the lease ends.
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    last_error      TEXT,
    processed_at    TIMESTAMPTZ,
    CONSTRAINT outbox_message_status_ck CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'DEAD'))
);

-- ⭐ Partial index: only rows the relay can still claim. Stays small and fast no matter how many
--    millions of DONE rows accumulate (they're excluded from the index entirely).
CREATE INDEX outbox_message_due_idx
    ON outbox_message (next_attempt_at)
    WHERE status IN ('PENDING', 'PROCESSING');
