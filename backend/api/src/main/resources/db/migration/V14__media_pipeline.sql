-- The transcode pipeline's jobs (Phase 7.4), written by the worker. docs/lld/video-pipeline.md §3, §9.
-- One row per unit of work; the DAG (PROBE → TRANSCODE × rung + THUMBNAILS → PACKAGE) unfolds as
-- jobs complete. Same lease protocol as the outbox: next_attempt_at is "due at" for PENDING and
-- the LEASE EXPIRY for RUNNING — a crashed worker's job simply becomes claimable again.
CREATE TABLE pipeline_job (
    id              UUID         PRIMARY KEY,
    asset_id        UUID         NOT NULL REFERENCES media_asset (id),
    type            VARCHAR(20)  NOT NULL,
    rung            VARCHAR(10)  NOT NULL DEFAULT '',  -- '' for jobs without a rung
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    worker_id       VARCHAR(100),                      -- who holds the lease
    progress        SMALLINT,                          -- 0..100, written at most every few seconds
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pipeline_job_type_ck   CHECK (type IN ('PROBE', 'TRANSCODE', 'THUMBNAILS', 'PACKAGE')),
    CONSTRAINT pipeline_job_status_ck CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'DEAD')),
    -- ⭐ invariant 1: a redelivered event or two rungs finishing at once enqueue NOTHING new
    CONSTRAINT pipeline_job_unit_uq   UNIQUE (asset_id, type, rung)
);

-- the claim query only; DONE rows pile up, this index doesn't
CREATE INDEX pipeline_job_due_idx ON pipeline_job (next_attempt_at) WHERE status IN ('PENDING', 'RUNNING');

-- ffprobe's answer the transcode jobs need (V13 has duration/width/height; audio was missing)
ALTER TABLE media_asset ADD COLUMN has_audio BOOLEAN;
