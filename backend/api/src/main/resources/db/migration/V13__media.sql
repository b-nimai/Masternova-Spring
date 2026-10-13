-- Media (Phase 7.3): an uploaded video and the resumable upload that creates it.
-- docs/lld/media.md §3, §9 · ADR-0014: the session stores the PLAN, never which parts arrived —
-- the storage provider is asked (ListParts).
CREATE TABLE media_asset (
    id                UUID         PRIMARY KEY,
    owner_id          UUID         NOT NULL REFERENCES app_user (id),
    kind              VARCHAR(10)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    duration_seconds  INT,                       -- from the worker's probe (PROCESSING on)
    width             INT,
    height            INT,
    failure_code      VARCHAR(40),               -- set with FAILED
    created_at        TIMESTAMPTZ  NOT NULL,
    ready_at          TIMESTAMPTZ,
    version           BIGINT       NOT NULL,
    CONSTRAINT media_asset_kind_ck   CHECK (kind IN ('VIDEO')),
    CONSTRAINT media_asset_status_ck CHECK (status IN ('AWAITING_UPLOAD', 'UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT media_asset_size_ck   CHECK (size_bytes > 0)
);

CREATE INDEX media_asset_owner_idx ON media_asset (owner_id, created_at DESC);

CREATE TABLE upload_session (
    id                 UUID         PRIMARY KEY,
    asset_id           UUID         NOT NULL UNIQUE REFERENCES media_asset (id),
    provider_upload_id VARCHAR(1024) NOT NULL,   -- S3's UploadId (opaque, can be long)
    object_key         VARCHAR(512) NOT NULL,
    size_bytes         BIGINT       NOT NULL,
    part_size_bytes    BIGINT       NOT NULL,
    part_count         INT          NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    completed_at       TIMESTAMPTZ,
    version            BIGINT       NOT NULL,
    CONSTRAINT upload_session_status_ck CHECK (status IN ('OPEN', 'COMPLETED', 'ABORTED', 'EXPIRED')),
    -- the PartPlan invariants, enforced again by the database
    CONSTRAINT upload_session_parts_ck  CHECK (part_count BETWEEN 1 AND 100),
    CONSTRAINT upload_session_psize_ck  CHECK (part_size_bytes >= 5242880),
    CONSTRAINT upload_session_plan_ck   CHECK (size_bytes <= part_size_bytes * part_count
                                               AND size_bytes > part_size_bytes * (part_count - 1))
);

-- ⭐ the orphan sweeper's only query (7.6): open sessions past their expiry. Partial: finished
--    sessions (the vast majority) are not in the index at all.
CREATE INDEX upload_session_open_expiry_idx ON upload_session (expires_at) WHERE status = 'OPEN';

-- One row per HLS rung of an asset, written (upserted) by the worker's pipeline (7.4).
-- ⭐ The primary key is why a crashed job's rerun can't duplicate a rendition (7.10).
CREATE TABLE media_rendition (
    asset_id      UUID        NOT NULL REFERENCES media_asset (id),
    rung          VARCHAR(10) NOT NULL,          -- '720p'
    width         INT         NOT NULL,
    height        INT         NOT NULL,
    bandwidth     INT         NOT NULL,          -- bits/s, for the master playlist's BANDWIDTH
    playlist_key  VARCHAR(512) NOT NULL,
    segment_count INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (asset_id, rung)
);
