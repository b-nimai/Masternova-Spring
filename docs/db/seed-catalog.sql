-- Catalog seed data — DEV and MEASUREMENT only, never production.
--
--   make seed                 # 200 courses, enough to scroll the Angular catalog
--   make seed COURSES=10000   # the dataset docs/db/indexes.md measures on
--
-- Deterministic (setseed): the same COURSES gives the same rows, so EXPLAIN numbers are comparable
-- between runs. Re-runnable: it removes its own previous rows (users @seed.test and their courses)
-- first. Needs the api's migrations applied (start the api once, or run an IT).
--
-- Two seeded logins (dev only — the {noop} password id is accepted by Spring's
-- DelegatingPasswordEncoder; real accounts are always {bcrypt}):
--   instructor@seed.test / seed-password   (INSTRUCTOR: owns most courses, sees /instructor)
--   learner@seed.test    / seed-password
--   admin@seed.test      / seed-password   (ADMIN: decides instructor applications, sees /admin)
--   learner2..learner50@seed.test / seed-password   (the k6 learners of load/slo-mix.js, D5.1)

\set ON_ERROR_STOP on
\if :{?courses}
\else
  \set courses 200
\endif

BEGIN;

-- rows that reference seeded courses go first (foreign keys): purchases (Phase 9), coupons,
-- entitlements (Phase 8) — including those of e2e users who bought a seeded course
DELETE FROM purchase_order WHERE id IN (
    SELECT l.order_id FROM order_line l JOIN course c ON c.id = l.course_id
    JOIN app_user u ON u.id = c.instructor_id WHERE u.email LIKE '%@seed.test');
DELETE FROM coupon WHERE course_id IN (
    SELECT c.id FROM course c JOIN app_user u ON u.id = c.instructor_id
    WHERE u.email LIKE '%@seed.test');
DELETE FROM entitlement WHERE course_id IN (
    SELECT c.id FROM course c JOIN app_user u ON u.id = c.instructor_id
    WHERE u.email LIKE '%@seed.test');
DELETE FROM course WHERE instructor_id IN (SELECT id FROM app_user WHERE email LIKE '%@seed.test');
-- media the seeded instructor uploaded (the media e2e logs in as them): jobs, sessions and
-- renditions reference the asset, the asset references its owner
DELETE FROM pipeline_job WHERE asset_id IN (SELECT a.id FROM media_asset a JOIN app_user u ON u.id = a.owner_id WHERE u.email LIKE '%@seed.test');
DELETE FROM upload_session WHERE asset_id IN (SELECT a.id FROM media_asset a JOIN app_user u ON u.id = a.owner_id WHERE u.email LIKE '%@seed.test');
DELETE FROM media_rendition WHERE asset_id IN (SELECT a.id FROM media_asset a JOIN app_user u ON u.id = a.owner_id WHERE u.email LIKE '%@seed.test');
DELETE FROM media_asset WHERE owner_id IN (SELECT id FROM app_user WHERE email LIKE '%@seed.test');
DELETE FROM app_user WHERE email LIKE '%@seed.test';

SELECT setseed(0.42);

-- 20 instructors, 50 learners, 1 admin. The first instructor is the one you can log in as.
INSERT INTO app_user (id, email, display_name, password_hash, email_verified_at, created_at, version)
SELECT gen_random_uuid(),
       CASE WHEN n = 1 THEN 'instructor@seed.test' ELSE 'instructor' || n || '@seed.test' END,
       (ARRAY['Asha Rao', 'Ravi Kumar', 'Meera Iyer', 'Arjun Shah', 'Priya Nair', 'Kabir Singh',
              'Ananya Das', 'Vikram Menon', 'Isha Kapoor', 'Rohan Gupta'])[1 + (n - 1) % 10]
           || CASE WHEN n > 10 THEN ' II' ELSE '' END,
       '{noop}seed-password', now(), now(), 0
FROM generate_series(1, 20) AS n;

INSERT INTO app_user (id, email, display_name, password_hash, email_verified_at, created_at, version)
VALUES (gen_random_uuid(), 'learner@seed.test', 'Lee Learner', '{noop}seed-password', now(), now(), 0),
       (gen_random_uuid(), 'admin@seed.test', 'Ada Admin', '{noop}seed-password', now(), now(), 0);

INSERT INTO app_user (id, email, display_name, password_hash, email_verified_at, created_at, version)
SELECT gen_random_uuid(), 'learner' || n || '@seed.test', 'Load Learner ' || n,
       '{noop}seed-password', now(), now(), 0
FROM generate_series(2, 50) AS n;

INSERT INTO app_user_role (user_id, role)
SELECT id, CASE WHEN email LIKE 'instructor%' THEN 'INSTRUCTOR' ELSE 'LEARNER' END
FROM app_user WHERE email LIKE '%@seed.test'
UNION ALL
SELECT id, 'ADMIN' FROM app_user WHERE email = 'admin@seed.test';

-- Courses. Topic × flavour titles; the instructor, child category, level, language, price, rating
-- and publish date vary pseudo-randomly. ~10 % stay drafts (published_at NULL).
CREATE TEMP TABLE seed_course ON COMMIT DROP AS
SELECT n,
       gen_random_uuid() AS id,
       topic,
       flavour,
       (SELECT id FROM app_user WHERE email LIKE 'instructor%@seed.test'
         ORDER BY email OFFSET (n * 7) % 20 LIMIT 1) AS instructor_id,
       (SELECT id FROM category WHERE parent_id IS NOT NULL
         ORDER BY slug OFFSET (n * 13) % 12 LIMIT 1) AS category_id,
       (ARRAY['BEGINNER', 'BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'ALL_LEVELS'])[1 + floor(random() * 5)::int] AS level,
       CASE WHEN random() < 0.15 THEN 'hi' ELSE 'en' END AS language,
       CASE WHEN random() < 0.15 THEN 0
            ELSE (ARRAY[49900, 99900, 149900, 199900, 299900, 449900])[1 + floor(random() * 6)::int]
       END AS price_minor,
       random() < 0.9 AS published,
       now() - (random() * interval '365 days') AS published_at,
       CASE WHEN random() < 0.2 THEN 0 ELSE 3 + floor(random() * 2000)::int END AS rating_count,
       round((3 + random() * 2)::numeric, 2) AS rating_average
FROM generate_series(1, :courses) AS n,
     LATERAL (SELECT (ARRAY['Kubernetes', 'Docker', 'Spring Boot', 'Angular', 'React', 'PostgreSQL',
                            'Terraform', 'Python', 'Machine Learning', 'Figma', 'System Design',
                            'Go', 'Rust', 'AWS', 'GitHub Actions', 'TypeScript', 'Kafka', 'Redis',
                            'Data Analysis', 'Product Marketing'])[1 + (n * 31) % 20] AS topic,
                     (ARRAY['from Zero', 'in Practice', 'Masterclass', 'for Beginners',
                            'Deep Dive', 'Bootcamp', 'Crash Course', 'in Production',
                            'Patterns', 'Interview Prep'])[1 + (n * 17) % 10] AS flavour) AS t;

INSERT INTO course (id, slug, title, subtitle, description, language, level, status, published_at,
                    price_minor, currency, price_set_at, instructor_id, instructor_name, category_id,
                    rating_average, rating_count, enrollment_count, lecture_count,
                    total_duration_seconds, created_at, updated_at, version)
SELECT s.id,
       lower(regexp_replace(s.topic || ' ' || s.flavour, '[^A-Za-z0-9]+', '-', 'g')) || '-' || s.n,
       s.topic || ' ' || s.flavour,
       'Hands-on ' || s.topic || ', explained step by step.',
       'A seeded course about ' || s.topic || '. Generated by docs/db/seed-catalog.sql.',
       s.language, s.level,
       CASE WHEN s.published THEN 'PUBLISHED' ELSE 'DRAFT' END,
       CASE WHEN s.published THEN date_trunc('second', s.published_at) END,
       s.price_minor, 'INR',
       -- ⭐ a published course has always confirmed its price (the publish gate's PRICE_NOT_SET);
       --    without it, "0" would read as "not priced yet" and a paid one as not for sale (9.9)
       CASE WHEN s.published THEN date_trunc('second', s.published_at) - interval '7 days' END,
       s.instructor_id, u.display_name, s.category_id,
       CASE WHEN s.rating_count = 0 THEN 0 ELSE s.rating_average END,
       s.rating_count,
       floor(random() * 5000)::int,
       12,                      -- 3 sections × 4 lectures, inserted below
       12 * 420,                -- 7 minutes each
       date_trunc('second', s.published_at) - interval '7 days',
       date_trunc('second', s.published_at),
       0
FROM seed_course s
JOIN app_user u ON u.id = s.instructor_id;

-- Ratings (Phase 11): a TALLY per rated course, and the course's summary computed from it — the
-- invariant engagement keeps (ADR-0023). A summary without a tally would be "repaired" to 0 by the
-- nightly reconcile. Mostly 4–5 stars; 5★ takes the rest so the counts add up exactly.
INSERT INTO course_rating (course_id, stars_1, stars_2, stars_3, stars_4, stars_5)
SELECT id, s1, s2, s3, s4, rating_count - (s1 + s2 + s3 + s4)
FROM (SELECT id, rating_count,
             floor(rating_count * 0.04 * random())::int AS s1,
             floor(rating_count * 0.06 * random())::int AS s2,
             floor(rating_count * 0.15 * random())::int AS s3,
             floor(rating_count * 0.40 * random())::int AS s4
      FROM seed_course WHERE published AND rating_count > 0) t;

UPDATE course c
SET rating_count = r.stars_1 + r.stars_2 + r.stars_3 + r.stars_4 + r.stars_5,
    rating_average = round((1 * r.stars_1 + 2 * r.stars_2 + 3 * r.stars_3 + 4 * r.stars_4
                            + 5 * r.stars_5)::numeric
                           / (r.stars_1 + r.stars_2 + r.stars_3 + r.stars_4 + r.stars_5), 2)
FROM course_rating r JOIN seed_course s ON s.id = r.course_id
WHERE c.id = r.course_id;

UPDATE course c SET rating_count = 0, rating_average = 0 -- drafts and unrated: no tally, no rating
FROM seed_course s
WHERE c.id = s.id AND NOT EXISTS (SELECT 1 FROM course_rating r WHERE r.course_id = c.id);

INSERT INTO section (id, course_id, title, position)
SELECT gen_random_uuid(), s.id, (ARRAY['Getting started', 'Core concepts', 'Going further'])[p + 1], p
FROM seed_course s, generate_series(0, 2) AS p;

INSERT INTO lecture (id, section_id, title, kind, position, preview, duration_seconds, asset_id)
SELECT gen_random_uuid(), sec.id, 'Lesson ' || (sec.position * 4 + p + 1),
       CASE WHEN p = 3 THEN 'ARTICLE' ELSE 'VIDEO' END,
       p, sec.position = 0 AND p = 0, 420, NULL
FROM section sec
JOIN seed_course s ON s.id = sec.course_id,
     generate_series(0, 3) AS p;

COMMIT;

ANALYZE category;
ANALYZE course;
ANALYZE section;
ANALYZE lecture;

SELECT count(*) FILTER (WHERE status = 'PUBLISHED') AS published,
       count(*) FILTER (WHERE status = 'DRAFT')     AS drafts
FROM course;
