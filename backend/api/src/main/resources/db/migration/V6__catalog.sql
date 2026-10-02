-- Catalog (Phase 5.2) — docs/lld/catalog.md §3, §9.
-- Only primary keys, unique constraints and foreign keys here. Every SECONDARY index arrives in
-- V7 (task 5.9), after EXPLAIN ANALYZE on 10,000 seeded courses — so "before" is a real point in
-- this schema's history, not a simulation.

CREATE TABLE category (
    id        UUID        PRIMARY KEY,
    slug      VARCHAR(80) NOT NULL UNIQUE,
    name      VARCHAR(80) NOT NULL,
    parent_id UUID        REFERENCES category (id),  -- NULL = a root; two levels only (seeded below)
    position  INT         NOT NULL                   -- display order among siblings
);

CREATE TABLE course (
    id                     UUID         PRIMARY KEY,
    slug                   VARCHAR(140) NOT NULL UNIQUE,  -- never regenerated on rename
    title                  VARCHAR(120) NOT NULL,
    subtitle               VARCHAR(200),
    description            TEXT         NOT NULL,
    language               VARCHAR(8)   NOT NULL,         -- ISO 639-1: en, hi, …
    level                  VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    published_at           TIMESTAMPTZ,                   -- first publish; never moves
    price_minor            BIGINT       NOT NULL,         -- Money: minor units (paise, cents)
    currency               VARCHAR(3)   NOT NULL,         -- Money: ISO 4217
    instructor_id          UUID         NOT NULL REFERENCES app_user (id),
    instructor_name        VARCHAR(100) NOT NULL,         -- snapshot (LLD §3)
    category_id            UUID         NOT NULL REFERENCES category (id),
    rating_average         NUMERIC(3, 2) NOT NULL DEFAULT 0,  -- written by engagement (Phase 11)
    rating_count           INT          NOT NULL DEFAULT 0,
    enrollment_count       INT          NOT NULL DEFAULT 0,   -- written by enrollment (Phase 10)
    lecture_count          INT          NOT NULL,             -- rollups kept by the aggregate
    total_duration_seconds INT          NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    version                BIGINT       NOT NULL,
    CONSTRAINT course_level_ck    CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'ALL_LEVELS')),
    CONSTRAINT course_status_ck   CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT course_price_ck    CHECK (price_minor >= 0),
    CONSTRAINT course_currency_ck CHECK (currency IN ('INR', 'USD')),
    CONSTRAINT course_rating_ck   CHECK (rating_average BETWEEN 0 AND 5 AND rating_count >= 0),
    -- ⭐ a published course always has its sort key: the public list never meets a NULL keyset
    --    value (ADR-0009 §3)
    CONSTRAINT course_published_at_ck CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE TABLE section (
    id        UUID         PRIMARY KEY,
    course_id UUID         NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    title     VARCHAR(120) NOT NULL,
    position  INT          NOT NULL,
    CONSTRAINT section_position_uq UNIQUE (course_id, position)  -- also serves "sections of a course"
);

CREATE TABLE lecture (
    id               UUID         PRIMARY KEY,
    section_id       UUID         NOT NULL REFERENCES section (id) ON DELETE CASCADE,
    title            VARCHAR(120) NOT NULL,
    kind             VARCHAR(20)  NOT NULL,
    position         INT          NOT NULL,
    preview          BOOLEAN      NOT NULL,   -- free to watch without buying (Phase 8)
    duration_seconds INT          NOT NULL,
    asset_id         UUID,                    -- media asset (Phase 7); SHARED by a duplicate
    CONSTRAINT lecture_kind_ck CHECK (kind IN ('VIDEO', 'ARTICLE')),
    CONSTRAINT lecture_duration_ck CHECK (duration_seconds >= 0),
    CONSTRAINT lecture_position_uq UNIQUE (section_id, position)
);

-- Reference data: the two-level category tree. Ids are generated; everything refers to slugs.
INSERT INTO category (id, slug, name, parent_id, position)
VALUES (gen_random_uuid(), 'development', 'Development', NULL, 0),
       (gen_random_uuid(), 'devops-cloud', 'DevOps & Cloud', NULL, 1),
       (gen_random_uuid(), 'data-ai', 'Data & AI', NULL, 2),
       (gen_random_uuid(), 'design', 'Design', NULL, 3),
       (gen_random_uuid(), 'business', 'Business', NULL, 4);

INSERT INTO category (id, slug, name, parent_id, position)
SELECT gen_random_uuid(), child.slug, child.name, root.id, child.position
FROM (VALUES ('development', 'web-development', 'Web Development', 0),
             ('development', 'mobile-development', 'Mobile Development', 1),
             ('development', 'programming-languages', 'Programming Languages', 2),
             ('devops-cloud', 'containers-kubernetes', 'Containers & Kubernetes', 0),
             ('devops-cloud', 'ci-cd', 'CI/CD', 1),
             ('devops-cloud', 'cloud-platforms', 'Cloud Platforms', 2),
             ('data-ai', 'data-science', 'Data Science', 0),
             ('data-ai', 'machine-learning', 'Machine Learning', 1),
             ('design', 'ui-ux', 'UI/UX', 0),
             ('design', 'graphic-design', 'Graphic Design', 1),
             ('business', 'entrepreneurship', 'Entrepreneurship', 0),
             ('business', 'marketing', 'Marketing', 1)) AS child (root_slug, slug, name, position)
JOIN category root ON root.slug = child.root_slug;
