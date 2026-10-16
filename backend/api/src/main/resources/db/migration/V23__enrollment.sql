-- Enrollment (Phase 10.2): one row per (user, course) — the learner's side of holding a course
-- ("My Learning", progress rollups). Follows entitlement by events (ADR-0021).
-- docs/lld/progress.md §3, §9.
CREATE TABLE enrollment (
    -- ⭐ CASCADE: an enrollment has no meaning without its user or course, and a listener running
    --    while one is deleted then fails on the FK (retried, then given up) instead of blocking it
    user_id            UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    course_id          UUID         NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    source             VARCHAR(20),             -- the source that (last) enrolled; NULL = a tombstone only
    status             VARCHAR(10)  NOT NULL,
    enrolled_at        TIMESTAMPTZ  NOT NULL,   -- first enrollment; a re-purchase keeps it
    -- ⭐ the time of the EVENT that set status: a grant or revoke applies only if it is newer, so
    --    events delivered twice, late or out of order can't flip it back (a revoke-first row is a
    --    REVOKED tombstone that the older grant can't resurrect)
    status_at          TIMESTAMPTZ  NOT NULL,
    -- rollups, refreshed by RECOUNT at each progress flush (10.4): idempotent, never +1
    completed_lectures INT          NOT NULL DEFAULT 0,
    last_lecture_id    UUID,                    -- no FK: a removed lecture is skipped at read
    last_activity_at   TIMESTAMPTZ,
    PRIMARY KEY (user_id, course_id),           -- the conflict target of both upserts
    CONSTRAINT enrollment_status_ck CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT enrollment_source_ck CHECK (source IN ('PURCHASE', 'FREE_ENROLLMENT', 'ADMIN_GRANT')),
    CONSTRAINT enrollment_completed_ck CHECK (completed_lectures >= 0)
);

-- "My Learning": the learner's ACTIVE courses, most recently watched first — one range scan
CREATE INDEX enrollment_dashboard_idx
    ON enrollment (user_id, last_activity_at DESC NULLS LAST) WHERE status = 'ACTIVE';
-- the course FK
CREATE INDEX enrollment_course_idx ON enrollment (course_id);

-- Backfill: access granted before Phase 10 (purchases, admin grants) enrolls too, and the
-- catalog's counter starts true. From here on the listener keeps both (ADR-0021).
INSERT INTO enrollment (user_id, course_id, source, status, enrolled_at, status_at)
SELECT user_id, course_id, source,
       CASE WHEN revoked_at IS NULL THEN 'ACTIVE' ELSE 'REVOKED' END,
       granted_at,
       COALESCE(revoked_at, granted_at)
FROM entitlement;

UPDATE course c
SET enrollment_count = (SELECT count(*) FROM enrollment e
                        WHERE e.course_id = c.id AND e.status = 'ACTIVE');
