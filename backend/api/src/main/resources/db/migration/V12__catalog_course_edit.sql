-- Curriculum undo/redo (Phase 6.5): one row per applied curriculum command, with the inverse that
-- undoes it. A TABLE, not an in-memory stack: the api runs as several replicas and gets redeployed,
-- and the tab that pressed Ctrl+Z may reach another process. docs/adr/0011-undo-with-stored-inverses.md
CREATE TABLE course_edit (
    id            UUID        PRIMARY KEY,
    course_id     UUID        NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    seq           BIGINT      NOT NULL,             -- order within the course: undo = highest done
    command       JSONB       NOT NULL,             -- the edit, as the client sent it (ids filled in)
    inverse       JSONB       NOT NULL,             -- its undo, captured while it was applied
    version_after BIGINT      NOT NULL,             -- the course version the edit produced
    actor_id      UUID        NOT NULL,
    undone_at     TIMESTAMPTZ,                      -- NULL = done; set = on the redo branch
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT course_edit_seq_uq UNIQUE (course_id, seq)  -- also serves "top of this course's stack"
);
