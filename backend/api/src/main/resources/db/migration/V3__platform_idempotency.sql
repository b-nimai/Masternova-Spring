-- Idempotency keys (platform kernel, Phase 2.6) — API conventions §4, docs/lld/platform-kernel.md.
-- One row per (caller, key): the FIRST request claims it, its response is stored, retries replay it.
CREATE TABLE idempotency_record (
    caller                VARCHAR(200) NOT NULL,   -- keys are scoped per caller, never global
    idem_key              VARCHAR(200) NOT NULL,
    request_hash          CHAR(64)     NOT NULL,   -- SHA-256 of method + path + body
    status                VARCHAR(20)  NOT NULL,
    response_status       INT,
    response_content_type VARCHAR(200),
    response_body         BYTEA,
    locked_until          TIMESTAMPTZ  NOT NULL,   -- an IN_PROGRESS claim older than this may be taken over
    created_at            TIMESTAMPTZ  NOT NULL,
    expires_at            TIMESTAMPTZ  NOT NULL,   -- after this the key may be reused
    PRIMARY KEY (caller, idem_key),
    CONSTRAINT idempotency_record_status_ck CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idempotency_record_expires_idx ON idempotency_record (expires_at);
