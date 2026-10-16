-- Lecture progress (Phase 10.3): where each learner is in each lecture — the FLUSHED truth behind
-- the Redis write-back buffer (ADR-0022). Written only by the flush's batched upsert, which
-- implements ProgressUpdate.merge: max only goes up, last is the newest. docs/lld/progress.md §9.
CREATE TABLE lecture_progress (
    user_id               UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- a lecture removed from the curriculum takes its progress with it
    lecture_id            UUID        NOT NULL REFERENCES lecture (id) ON DELETE CASCADE,
    course_id             UUID        NOT NULL REFERENCES course (id) ON DELETE CASCADE, -- from catalog at the flush
    last_position_seconds INT         NOT NULL,   -- the resume point (newest heartbeat)
    max_position_seconds  INT         NOT NULL,   -- monotonic: how far they have ever got
    updated_at            TIMESTAMPTZ NOT NULL,   -- the newest heartbeat's time (orders "last")
    completed_at          TIMESTAMPTZ,            -- set once at ≥ 90 % of the duration, never unset
    PRIMARY KEY (user_id, lecture_id),
    -- ⭐ the merge's invariant, enforced by the database too
    CONSTRAINT lecture_progress_positions_ck
        CHECK (last_position_seconds >= 0 AND max_position_seconds >= last_position_seconds)
);

-- the rollup recount and the course page: one learner's rows in one course
CREATE INDEX lecture_progress_course_idx ON lecture_progress (user_id, course_id);
