-- Engagement: lecture Q&A threads (Phase 11.2). docs/lld/engagement.md §3, §5, §9.

CREATE TABLE question (
    id               UUID          PRIMARY KEY,
    course_id        UUID          NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    -- ⭐ no FK: removing a lecture HIDES its questions (the lecture is a 404) instead of deleting
    --    them, so the curriculum's undo (ADR-0011: the same lecture id comes back) brings them back
    lecture_id       UUID          NOT NULL,
    author_id        UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    author_name      VARCHAR(100)  NOT NULL,  -- snapshot (catalog LLD §3)
    title            VARCHAR(150)  NOT NULL,
    body             VARCHAR(5000) NOT NULL DEFAULT '',
    answer_count     INT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_activity_at TIMESTAMPTZ   NOT NULL,  -- the newest answer's time (or created_at)
    CONSTRAINT question_title_ck CHECK (length(btrim(title)) > 0),
    CONSTRAINT question_answer_count_ck CHECK (answer_count >= 0)
);

-- A lecture's questions, newest first, keyset (created_at, id)
CREATE INDEX question_lecture_newest_idx ON question (lecture_id, created_at DESC, id DESC);
-- the FKs (a course's or a user's deletion cascades)
CREATE INDEX question_course_idx ON question (course_id);
CREATE INDEX question_author_idx ON question (author_id);

CREATE TABLE answer (
    id          UUID          PRIMARY KEY,
    question_id UUID          NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    parent_id   UUID,                          -- NULL = answers the question itself
    depth       SMALLINT      NOT NULL,        -- 1 = an answer, 2–3 = replies
    author_id   UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    author_name VARCHAR(100)  NOT NULL,
    body        VARCHAR(5000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT answer_body_ck CHECK (length(btrim(body)) > 0),
    CONSTRAINT answer_depth_ck CHECK (depth BETWEEN 1 AND 3),
    CONSTRAINT answer_root_ck CHECK ((parent_id IS NULL) = (depth = 1)),
    -- ⭐ a COMPOSITE foreign key: a reply's parent exists AND is in the same question, so a thread
    --    can't borrow another thread's answers (the target needs this unique key)
    CONSTRAINT answer_in_question_uq UNIQUE (question_id, id),
    CONSTRAINT answer_parent_fk FOREIGN KEY (question_id, parent_id)
        REFERENCES answer (question_id, id) ON DELETE CASCADE
);

-- Each level of the recursive thread read: the children of (question, parent)
CREATE INDEX answer_thread_idx ON answer (question_id, parent_id, created_at);
CREATE INDEX answer_author_idx ON answer (author_id);
