#!/usr/bin/env python3
"""Measures every catalog list query with EXPLAIN (ANALYZE, BUFFERS) — docs/db/indexes.md.

    make seed COURSES=10000          # the dataset (deterministic)
    python3 docs/db/measure_catalog.py [label]

Each query runs 7 times; the MEDIAN execution time is reported (the first run warms the cache, so
the median is a warm-cache number — what a busy catalog sees), with the plan's main nodes. The SQL
is what Hibernate actually sends (captured with logging.level.org.hibernate.SQL=DEBUG), with the
parameters filled in.
"""
import re
import statistics
import subprocess
import sys

RUNS = 7

# the columns Hibernate selects for a list page (course + its category)
COLUMNS = (
    "c1_0.id,c1_0.category_id,c2_0.id,c2_0.name,c2_0.parent_id,c2_0.position,c2_0.slug,"
    "c1_0.created_at,c1_0.description,c1_0.enrollment_count,c1_0.instructor_id,"
    "c1_0.instructor_name,c1_0.language,c1_0.lecture_count,c1_0.level,c1_0.price_minor,"
    "c1_0.currency,c1_0.published_at,c1_0.rating_average,c1_0.rating_count,c1_0.slug,"
    "c1_0.status,c1_0.subtitle,c1_0.title,c1_0.total_duration_seconds,c1_0.updated_at,c1_0.version"
)
FROM = "from course c1_0 join category c2_0 on c2_0.id=c1_0.category_id"


def psql(sql: str) -> str:
    result = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-At", "-U", "masternova",
         "-d", "masternova", "-c", sql],
        capture_output=True, text=True, check=True)
    return result.stdout.strip()


def one(sql: str) -> str:
    return psql(sql).splitlines()[0]


def queries() -> list[tuple[str, str]]:
    # the 5,000th published course in NEWEST order: where page 250 of 20 starts
    deep_at, deep_id = one(
        "select published_at, id from course where status='PUBLISHED' "
        "order by published_at desc, id desc offset 4999 limit 1").split("|")
    devops = ",".join(
        f"'{i}'" for i in psql(
            "select c.id from category c left join category p on p.id=c.parent_id "
            "where c.slug='devops-cloud' or p.slug='devops-cloud'").splitlines())
    instructor = one("select id from app_user where email='instructor@seed.test'")
    slug = one("select slug from course where status='PUBLISHED' order by slug limit 1")
    sections = ",".join(f"'{i}'" for i in psql(
        f"select s.id from section s join course c on c.id=s.course_id where c.slug='{slug}'"
    ).splitlines())
    newest = "order by c1_0.published_at desc,c1_0.id desc fetch first 21 rows only"
    return [
        ("Q1 browse, NEWEST, page 1",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and 1=1 {newest}"),
        ("Q2 browse, NEWEST, page 250 — keyset as Spring Data emits it (OR chain)",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and 1=1 and "
         f"(c1_0.published_at<'{deep_at}' or c1_0.published_at='{deep_at}' and c1_0.id<'{deep_id}') "
         f"{newest}"),
        ("Q2b browse, NEWEST, page 250 — OR chain + redundant bound published_at <= key (shipped)",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and c1_0.published_at<='{deep_at}' "
         f"and 1=1 and "
         f"(c1_0.published_at<'{deep_at}' or c1_0.published_at='{deep_at}' and c1_0.id<'{deep_id}') "
         f"{newest}"),
        ("Q2r browse, NEWEST, page 250 — keyset as a row comparison (hand-written)",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and "
         f"(c1_0.published_at, c1_0.id) < ('{deep_at}', '{deep_id}') {newest}"),
        ("Q2o browse, NEWEST, page 250 — OFFSET 4980 (what Pageable would send)",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' "
         f"order by c1_0.published_at desc,c1_0.id desc offset 4980 rows fetch first 21 rows only"),
        ("Q3 category tree (devops-cloud + 3 children), NEWEST",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and c1_0.category_id in ({devops}) "
         f"and 1=1 {newest}"),
        ("Q4 HIGHEST_RATED, page 1",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and 1=1 "
         f"order by c1_0.rating_average desc,c1_0.id desc fetch first 21 rows only"),
        ("Q5 PRICE_LOW, page 1",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and 1=1 "
         f"order by c1_0.price_minor,c1_0.id fetch first 21 rows only"),
        ("Q5h PRICE_HIGH, page 1",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and 1=1 "
         f"order by c1_0.price_minor desc,c1_0.id desc fetch first 21 rows only"),
        ("Q6 title search 'kube', NEWEST",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and lower(c1_0.title) like "
         f"'%kube%' escape '\\' and 1=1 {newest}"),
        ("Q6x title search with no match 'xyzzy', NEWEST",
         f"select {COLUMNS} {FROM} where c1_0.status='PUBLISHED' and lower(c1_0.title) like "
         f"'%xyzzy%' escape '\\' and 1=1 {newest}"),
        ("Q7 instructor's own list, RECENTLY_UPDATED",
         f"select {COLUMNS} {FROM} where c1_0.instructor_id='{instructor}' and 1=1 "
         f"order by c1_0.updated_at desc,c1_0.id desc fetch first 21 rows only"),
        ("Q8 course page: course + category + sections (entity graph)",
         f"select c1_0.*, c2_0.*, s1_0.* from course c1_0 join category c2_0 on c2_0.id=c1_0.category_id "
         f"left join section s1_0 on c1_0.id=s1_0.course_id where c1_0.slug='{slug}' "
         f"order by s1_0.position"),
        ("Q8b course page: lectures of its sections (@BatchSize)",
         f"select l1_0.* from lecture l1_0 where l1_0.section_id in ({sections}) "
         f"order by l1_0.section_id, l1_0.position"),
    ]


def measure(sql: str) -> tuple[float, str]:
    times, plan = [], ""
    for _ in range(RUNS):
        out = psql(f"explain (analyze, buffers, costs off) {sql}")
        times.append(float(re.search(r"Execution Time: ([\d.]+) ms", out).group(1)))
        plan = out
    nodes = []
    for line in plan.splitlines():
        text = re.sub(r"\s*\(actual[^)]*\)", "", line).replace("->", "").strip()
        if text.startswith("Sort Key:"):
            continue
        if text.startswith("Sort Method:"):
            nodes[-1] += " (" + text.removeprefix("Sort Method: ").split("  ")[0] + ")"
        elif re.match(r"(Limit|Sort|Incremental Sort|Hash Join|Nested Loop|Merge Join|"
                      r"Seq Scan|Index Scan|Index Only Scan|Bitmap Heap Scan|Bitmap Index Scan)",
                      text):
            nodes.append(text)
    return statistics.median(times), " → ".join(nodes[:6])


if __name__ == "__main__":
    label = sys.argv[1] if len(sys.argv) > 1 else "run"
    print(f"## {label} ({one('select count(*) from course')} courses, median of {RUNS})\n")
    print("| Query | ms | Plan (main nodes) |\n|---|---:|---|")
    for name, sql in queries():
        ms, plan = measure(sql)
        print(f"| {name} | {ms:.3f} | {plan} |")
