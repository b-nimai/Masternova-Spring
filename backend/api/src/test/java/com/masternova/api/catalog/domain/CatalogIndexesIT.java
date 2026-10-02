package com.masternova.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * ⭐ Pins the SHAPE of the catalog's query plans (docs/db/indexes.md), so a dropped index or a lost
 * keyset bound fails the build instead of quietly making page 250 slow again.
 *
 * <p>The test database is tiny, where a sequential scan is honestly the cheapest plan; {@code SET
 * LOCAL enable_seqscan = off} asks the planner what it WOULD do with an index — which is the
 * question: can the index serve this query, and is the cursor a seek or a filter?
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CatalogIndexesIT {

  static final String CURSOR = "'2026-03-16 10:02:31+00'";
  static final String ID = "'3f2c0000-0000-4000-8000-000000000001'";

  @Autowired DataSource dataSource;
  JdbcClient jdbc;

  @BeforeEach
  void preferIndexes() {
    jdbc = JdbcClient.create(dataSource); // joins the test transaction, so SET LOCAL applies
    jdbc.sql("SET LOCAL enable_seqscan = off").update();
  }

  private String plan(String where, String orderBy) {
    List<String> lines =
        jdbc.sql(
                "EXPLAIN (COSTS OFF) SELECT * FROM course WHERE "
                    + where
                    + " ORDER BY "
                    + orderBy
                    + " LIMIT 21")
            .query(String.class)
            .list();
    return String.join("\n", lines);
  }

  @Test
  void theDefaultListWalksThePartialIndexWithNoSort() {
    String plan = plan("status = 'PUBLISHED'", "published_at DESC, id DESC");

    assertThat(plan).contains("course_published_newest_idx").doesNotContain("Sort");
  }

  /**
   * ⭐ The finding of 5.9: Spring Data's OR chain alone is a FILTER, so the scan starts at row 1.
   */
  @Test
  void theOrChainAloneCannotSeekButTheRedundantBoundCan() {
    String orChain =
        "(published_at < %s OR (published_at = %s AND id < %s))".formatted(CURSOR, CURSOR, ID);

    String withoutBound = plan("status = 'PUBLISHED' AND " + orChain, "published_at DESC, id DESC");
    String withBound =
        plan(
            "status = 'PUBLISHED' AND published_at <= " + CURSOR + " AND " + orChain,
            "published_at DESC, id DESC");

    assertThat(withoutBound).doesNotContain("Index Cond: (published_at");
    assertThat(withBound)
        .contains("course_published_newest_idx")
        .contains("Index Cond: (published_at <="); // ⭐ the scan STARTS at the cursor
  }

  @Test
  void eachPublicSortHasAnIndexInItsOwnOrder() {
    assertThat(plan("status = 'PUBLISHED'", "rating_average DESC, id DESC"))
        .contains("course_published_rating_idx")
        .doesNotContain("Sort");
    assertThat(plan("status = 'PUBLISHED'", "price_minor, id"))
        .contains("course_published_price_idx")
        .doesNotContain("Sort");
    assertThat(plan("status = 'PUBLISHED'", "price_minor DESC, id DESC"))
        .contains("Index Scan Backward using course_published_price_idx"); // one index, two ways
  }

  @Test
  void theInstructorListAndTitleSearchHaveTheirIndexes() {
    assertThat(plan("instructor_id = " + ID, "updated_at DESC, id DESC"))
        .contains("course_instructor_updated_idx")
        .doesNotContain("Sort");
    assertThat(plan("lower(title) LIKE '%xyzzy%'", "published_at DESC, id DESC"))
        .contains("course_title_trgm_idx");
  }
}
