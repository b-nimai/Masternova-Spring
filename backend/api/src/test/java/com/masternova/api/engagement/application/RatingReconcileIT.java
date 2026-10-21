package com.masternova.api.engagement.application;

import static com.masternova.api.engagement.TestEngagement.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.engagement.TestEngagement;
import com.masternova.api.engagement.application.RatingReconciler.Repairs;
import com.masternova.api.engagement.domain.RatingTally;
import com.masternova.api.engagement.domain.ReviewRepository;
import com.masternova.api.entitlement.TestCourses;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The nightly net (ADR-0023 §4): repairs what wrote around the code, and never erases a review. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RatingReconcileIT {

  @Autowired DataSource dataSource;
  @Autowired ReviewService reviews;
  @Autowired ReviewRepository repository;
  @Autowired RatingReconciler reconciler;
  @Autowired TransactionTemplate tx;
  TestEngagement engagement;
  JdbcClient jdbc;
  UUID course;
  UUID ana;
  UUID ben;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    engagement = new TestEngagement(dataSource);
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    course = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, null).courseId();
    ana = people.create("Ana").id();
    ben = people.create("Ben").id();
    engagement.enroll(ana, course);
    engagement.enroll(ben, course);
    reviews.write(user(ana), course, 5, "");
    reviews.write(user(ben), course, 1, "");
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  @Test
  void aDeletedUsersReviewsAreRecountedAndCatalogFollows() {
    assertThat(reconciler.reconcile()).isEqualTo(new Repairs(0, 0)); // nothing to do yet

    deleteUser(ben); // ⭐ cascades to Ben's review; no Java ran: the tally still counts it
    assertThat(engagement.catalogRating(course)).containsExactly(2, 300);

    assertThat(reconciler.reconcile()).isEqualTo(new Repairs(1, 0));
    assertThat(repository.tally(course)).contains(RatingTally.of(0, 0, 0, 0, 1));
    assertThat(engagement.catalogRating(course)).containsExactly(1, 500);
    assertThat(reconciler.reconcile()).isEqualTo(new Repairs(0, 0)); // idempotent
  }

  @Test
  void aStaleCatalogSummaryIsRepaired() {
    // a pre-Phase-11 pod saving the course with the summary it loaded before the reviews
    jdbc.sql("UPDATE course SET rating_count = 0, rating_average = 0 WHERE id = :c")
        .param("c", course)
        .update();

    assertThat(reconciler.reconcile()).isEqualTo(new Repairs(0, 1));
    assertThat(engagement.catalogRating(course)).containsExactly(2, 300);
  }

  /** A rating no review explains (written by SQL, or seeded): the course has no tally at all. */
  @Test
  void aRatingNoTallyExplainsIsReset() {
    UUID unreviewed =
        new TestCourses(dataSource)
            .create(new TestInstructors(dataSource).create("Omar").id(), "PUBLISHED", 0, true, null)
            .courseId();
    jdbc.sql("UPDATE course SET rating_count = 1500, rating_average = 4.7 WHERE id = :c")
        .param("c", unreviewed)
        .update();

    assertThat(reconciler.reconcile()).isEqualTo(new Repairs(0, 1));
    assertThat(engagement.catalogRating(unreviewed)).containsExactly(0, 0);
    assertThat(repository.tally(unreviewed)).contains(RatingTally.empty());
    assertThat(reconciler.reconcile()).isEqualTo(new Repairs(0, 0));
  }

  /**
   * ⭐ A review in flight while the job repairs the same course: the job waits on the tally lock and
   * recounts AFTER it — so the new review is in the repaired tally, not erased by it.
   */
  @Test
  void aReviewCommittedWhileTheRepairWaitsIsCounted() throws Exception {
    deleteUser(ben); // drift: the tally says 2 reviews, there is 1
    UUID cleo = new TestInstructors(dataSource).create("Cleo").id();
    engagement.enroll(cleo, course);

    CountDownLatch written = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> review =
        CompletableFuture.runAsync(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      reviews.write(user(cleo), course, 4, ""); // holds the tally lock …
                      written.countDown();
                      hold(release); // … until the job is waiting for it
                    }));
    assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Repairs> repair = CompletableFuture.supplyAsync(reconciler::reconcile);
    await().atMost(Duration.ofSeconds(10)).until(this::someoneWaitsForALock);
    release.countDown();
    review.get(10, TimeUnit.SECONDS);
    repair.get(10, TimeUnit.SECONDS);

    // Ana's 5 and Cleo's 4: Ben's vanished 1 is gone, Cleo's in-flight 4 is kept
    assertThat(repository.tally(course)).contains(RatingTally.of(0, 0, 0, 1, 1));
    assertThat(engagement.catalogRating(course)).containsExactly(2, 450);
  }

  private void deleteUser(UUID id) {
    jdbc.sql("DELETE FROM enrollment WHERE user_id = :u").param("u", id).update();
    jdbc.sql("DELETE FROM app_user WHERE id = :u").param("u", id).update();
  }

  private boolean someoneWaitsForALock() {
    return jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            .query(Long.class)
            .single()
        > 0;
  }

  private static void hold(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
