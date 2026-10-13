-- ShedLock (Phase 7.6): one row per scheduled task. A worker replica runs a @SchedulerLock'd task
-- only after taking the row (lock_until in the future, in the DATABASE's clock), so N replicas run
-- the orphan sweeper once, not N times. The job claim loop doesn't need it: SKIP LOCKED shares it.
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
