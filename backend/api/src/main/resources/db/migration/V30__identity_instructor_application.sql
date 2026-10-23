-- Instructor applications (Phase UI.10) — docs/lld/identity.md §3, §9.
-- One row per user: a rejected (or later demoted) applicant re-applies by resubmitting the same row.
CREATE TABLE instructor_application (
    user_id    UUID        PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    pitch      VARCHAR(500),                              -- what they want to teach; optional
    applied_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decided_by UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT instructor_application_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- a decision has a time exactly when it is not pending
    CONSTRAINT instructor_application_decided_ck CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

-- The admin's queue: pending, oldest first. Partial, so decided rows don't grow it.
CREATE INDEX instructor_application_pending_idx
    ON instructor_application (applied_at) WHERE status = 'PENDING';
-- decided_by is a foreign key: index it (note 10 §10)
CREATE INDEX instructor_application_decided_by_idx ON instructor_application (decided_by);
