-- Entitlement (Phase 8.3): one row per (user, course) — who may watch a course beyond its free
-- and preview lectures. docs/lld/entitlement-engine.md §3, §9 · ADR-0016 (the row is cached).
CREATE TABLE entitlement (
    user_id       UUID         NOT NULL REFERENCES app_user (id),
    course_id     UUID         NOT NULL REFERENCES course (id),
    source        VARCHAR(20)  NOT NULL,
    granted_at    TIMESTAMPTZ  NOT NULL,
    expires_at    TIMESTAMPTZ,               -- NULL = lifetime access
    revoked_at    TIMESTAMPTZ,               -- set by a refund / an admin; wins over expiry
    revoke_reason VARCHAR(200),
    -- ⭐ the hot lookup is a primary-key hit, and the key is what makes a grant idempotent:
    --    50 concurrent copies of one webhook upsert ONE row
    PRIMARY KEY (user_id, course_id),
    CONSTRAINT entitlement_source_ck  CHECK (source IN ('PURCHASE', 'FREE_ENROLLMENT', 'ADMIN_GRANT')),
    CONSTRAINT entitlement_expiry_ck  CHECK (expires_at IS NULL OR expires_at > granted_at),
    CONSTRAINT entitlement_revoked_ck CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

-- the course FK, and "who holds this course" (Phase 10's learner lists)
CREATE INDEX entitlement_course_idx ON entitlement (course_id);
