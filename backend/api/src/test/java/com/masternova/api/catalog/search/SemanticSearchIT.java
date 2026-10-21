package com.masternova.api.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.application.CourseSearchService;
import com.masternova.api.catalog.application.CourseSearchService.SearchResult;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.catalog.domain.search.SearchMode;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Semantic and hybrid search end to end, with a deterministic model in place of Ollama: the sweeper
 * keeps embeddings fresh, the HNSW query finds by direction, and a broken model degrades to
 * keywords.
 */
@SpringBootTest(
    properties = {
      "masternova.search.embedding.enabled=true",
      "masternova.search.embedding.index-enabled=false", // the test runs the sweep by hand
      "masternova.search.embedding.max-distance=0.95", // hashed words are far apart; Ollama's
      // aren't
      "masternova.search.embedding.query-timeout=2s",
      // ⭐ a test table is tiny, so the planner would scan it and sort: force the HNSW index, the
      //    path production takes (and the one with the ef_search cap)
      "spring.datasource.hikari.connection-init-sql=SET enable_seqscan = off; SET enable_sort = off"
    })
@Import({
  TestcontainersConfiguration.class,
  SemanticSearchIT.Model.class,
  com.masternova.api.catalog.infrastructure.search.TestIndexer.class
})
class SemanticSearchIT {

  @TestConfiguration(proxyBeanMethods = false)
  static class Model {
    @Bean
    @Primary
    HashingEmbeddingModel testEmbeddingModel() {
      return new HashingEmbeddingModel();
    }
  }

  @Autowired DataSource dataSource;
  @Autowired CourseSearchService search;
  @Autowired HashingEmbeddingModel model;
  @Autowired com.masternova.api.catalog.infrastructure.search.TestIndexer indexer;
  JdbcClient jdbc;
  SearchFixtures courses;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    model.failing(false);
    courses =
        new SearchFixtures(dataSource, new TestInstructors(dataSource).create("Asha Rao").id());
  }

  @AfterEach
  void clean() {
    model.failing(false);
    new TestInstructors(dataSource).deleteAll(); // courses cascade to embeddings
    // ⭐ what autovacuum does in production (V28 tunes it for this table): HNSW keeps deleted
    //    entries in its graph until VACUUM, and the next test's few rows would be unreachable
    //    through a graph of dead ones (docs/db/indexes.md §9)
    jdbc.sql("VACUUM course_embedding").update();
  }

  @Test
  void theSweepEmbedsEditsReEmbedsAndForgetsUnpublishedCourses() {
    UUID a = courses.published("Orchestration", null, "Pods, nodes and clusters.");
    UUID draft = courses.course("Draft", null, "Not public.", "en", "DRAFT");
    assertThat(indexer.run()).containsExactly(1, 0);
    assertThat(indexer.run()).containsExactly(0, 0); // nothing stale: no model call
    assertThat(embedded()).containsExactly(a);

    courses.edit(a, "Pods, nodes, clusters and services."); // the hash no longer matches
    assertThat(indexer.run()).containsExactly(1, 0);

    jdbc.sql("UPDATE course_embedding SET model = 'old-model'").update(); // a model change
    assertThat(indexer.run()).containsExactly(1, 0);

    jdbc.sql("UPDATE course SET status = 'ARCHIVED' WHERE id = :id").param("id", a).update();
    assertThat(indexer.run()).containsExactly(0, 1);
    assertThat(embedded()).isEmpty();
    assertThat(draft).isNotNull();
  }

  @Test
  void aFailedBatchWritesNothingAndTheNextRunRetries() {
    courses.published("Orchestration", null, "Pods.");
    model.failing(true);
    assertThatThrownBy(indexer::run).isInstanceOf(IllegalStateException.class);
    assertThat(embedded()).isEmpty();

    model.failing(false);
    assertThat(indexer.run()).containsExactly(1, 0);
  }

  @Test
  void semanticSearchFindsWhatSharesMeaningNotJustTheTitle() {
    UUID pods = courses.published("Orchestration", null, "pods nodes clusters scheduling");
    courses.published("Watercolour", null, "brushes paper pigment washes");
    indexer.run();

    SearchResult result = search.search("scheduling pods on nodes", SearchMode.SEMANTIC, 10);

    assertThat(result.ran()).isEqualTo(SearchMode.SEMANTIC);
    assertThat(result.degraded()).isFalse();
    assertThat(ids(result)).containsExactly(pods); // the watercolour course is past max-distance
  }

  /**
   * ⭐ pgvector's HNSW scan returns at most {@code hnsw.ef_search} rows (default 40), whatever the
   * LIMIT: found by EXPLAIN on 10,000 courses (docs/db/indexes.md §9). The ranker raises it per
   * query.
   */
  @Test
  void moreThanFortyNeighboursCanBeAskedFor() {
    for (int i = 0; i < 55; i++) {
      courses.published("Course " + i, null, "pods");
    }
    indexer.run(); // 32 per batch
    indexer.run();

    assertThat(search.search("pods", SearchMode.SEMANTIC, 50).courses()).hasSize(50);
  }

  @Test
  void hybridRanksACourseBothRankersFoundFirst() {
    UUID both = courses.published("Kubernetes scheduling", null, "pods nodes scheduling");
    UUID wordsOnly = courses.published("Kubernetes history", null, "a timeline of releases");
    indexer.run();

    SearchResult result = search.search("kubernetes scheduling pods", SearchMode.HYBRID, 10);

    assertThat(result.ran()).isEqualTo(SearchMode.HYBRID);
    assertThat(ids(result)).startsWith(both).contains(wordsOnly);
  }

  @Test
  void aBrokenModelDegradesToKeywordsInsteadOfFailing() {
    UUID k8s = courses.published("Kubernetes in practice", null, "pods");
    indexer.run();
    model.failing(true);

    for (SearchMode mode : List.of(SearchMode.SEMANTIC, SearchMode.HYBRID)) {
      SearchResult result = search.search("kubernetes", mode, 10);
      assertThat(result.ran()).isEqualTo(SearchMode.KEYWORD);
      assertThat(result.degraded()).isTrue();
      assertThat(ids(result)).containsExactly(k8s);
    }
  }

  private List<UUID> embedded() {
    return jdbc.sql("SELECT course_id FROM course_embedding").query(UUID.class).list();
  }

  private static List<UUID> ids(SearchResult result) {
    return result.courses().stream().map(Course::id).toList();
  }
}
