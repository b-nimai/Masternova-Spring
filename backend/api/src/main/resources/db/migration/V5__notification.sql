-- Notification (Phase 4) — docs/lld/notification.md §3, §9. The api owns the schema for both
-- deployables: the worker writes email_delivery and reads the other two; the api writes
-- preferences and suppressions.

-- ⭐ One row IS one email. The unique key is the claim: whoever inserts (or legally re-claims) the
--    row sends; a redelivered event finds the row and does not send again.
CREATE TABLE email_delivery (
    id                  UUID         PRIMARY KEY,
    event_id            UUID         NOT NULL,              -- the outbox message that caused it
    template            VARCHAR(100) NOT NULL,              -- e.g. verify-email
    recipient           CITEXT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    attempts            INT          NOT NULL DEFAULT 1,
    provider_message_id VARCHAR(200),
    last_error          TEXT,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,              -- for SENDING rows: when the claim was taken
    CONSTRAINT email_delivery_once_uk UNIQUE (event_id, template, recipient),
    CONSTRAINT email_delivery_status_ck
        CHECK (status IN ('SENDING', 'SENT', 'FAILED', 'BOUNCED', 'SUPPRESSED'))
);

-- Presence forbids every send to the address, of every category (deliverability is shared).
CREATE TABLE email_suppression (
    email      CITEXT       PRIMARY KEY,
    reason     VARCHAR(20)  NOT NULL,
    detail     TEXT,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT email_suppression_reason_ck CHECK (reason IN ('BOUNCED', 'COMPLAINED', 'MANUAL'))
);

-- Absent = subscribed: a new account and a new category need no rows.
CREATE TABLE notification_preference (
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    category   VARCHAR(40) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, category)
);
