-- Engagement: reviews and the exact rating tally (Phase 11.1). docs/lld/engagement.md §3, §9,
-- ADR-0023.

CREATE TABLE review (
    id          UUID          PRIMARY KEY,
    -- ⭐ CASCADE: a review has no meaning without its course or author. A user's deletion removes
    --    their reviews WITHOUT touching course_rating: the nightly reconcile repairs that tally
    course_id   UUID          NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    rating      SMALLINT      NOT NULL,
    body        VARCHAR(2000) NOT NULL DEFAULT '',  -- '' = a rating without words
    author_name VARCHAR(100)  NOT NULL,             -- snapshot at the last write (catalog LLD §3)
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT review_rating_ck CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT review_one_each_uq UNIQUE (course_id, user_id)  -- also: "my review of this course"
);

-- The course page: newest first, keyset (created_at, id) — one range scan per page
CREATE INDEX review_course_newest_idx ON review (course_id, created_at DESC, id DESC);
-- the user FK (a user's deletion cascades)
CREATE INDEX review_user_idx ON review (user_id);

-- ⭐ The exact state of a course's rating: five integer counters. Count, sum and average are
--    DERIVED (RatingTally), so there is nothing here that could disagree with anything else.
--    This row is also the LOCK every review write of the course takes first (ADR-0023 §2).
CREATE TABLE course_rating (
    course_id UUID PRIMARY KEY REFERENCES course (id) ON DELETE CASCADE,
    stars_1   INT  NOT NULL DEFAULT 0,
    stars_2   INT  NOT NULL DEFAULT 0,
    stars_3   INT  NOT NULL DEFAULT 0,
    stars_4   INT  NOT NULL DEFAULT 0,
    stars_5   INT  NOT NULL DEFAULT 0,
    CONSTRAINT course_rating_ck CHECK (stars_1 >= 0 AND stars_2 >= 0 AND stars_3 >= 0
                                       AND stars_4 >= 0 AND stars_5 >= 0)
);
