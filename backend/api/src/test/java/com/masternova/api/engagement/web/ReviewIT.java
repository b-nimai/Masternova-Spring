package com.masternova.api.engagement.web;

import static com.masternova.api.engagement.TestEngagement.as;
import static com.masternova.api.engagement.TestEngagement.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.engagement.TestEngagement;
import com.masternova.api.engagement.application.ReviewService;
import com.masternova.api.engagement.domain.RatingTally;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.identity.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Reviews keep the course's rating EXACT (ADR-0023): every write moves the tally and catalog's
 * columns once, however many requests race.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReviewIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired ReviewService reviews;
  TestEngagement engagement;
  JdbcClient jdbc;
  UUID instructor;
  UUID learner;
  UUID course;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    engagement = new TestEngagement(dataSource);
    TestInstructors people = new TestInstructors(dataSource);
    instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, null).courseId();
    engagement.enroll(learner, course);
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll(); // courses cascade to reviews and tallies
  }

  @Test
  void writingEditingAndDeletingMoveTheRatingExactly() {
    assertThat(put(learner, 4, "Clear and practical.")).hasStatusOk();
    UUID other = enrolledLearner("Omar Other");
    assertThat(put(other, 5, "")).hasStatusOk();
    assertThat(tally()).isEqualTo(RatingTally.of(0, 0, 0, 1, 1));
    assertThat(engagement.catalogRating(course)).containsExactly(2, 450);

    var edited = assertThat(put(learner, 2, "Second half is rushed.")).hasStatusOk().bodyJson();
    edited.extractingPath("$.rating").isEqualTo(2);
    edited.extractingPath("$.edited").isEqualTo(true);
    edited.extractingPath("$.authorName").isEqualTo("Lena Learner");
    assertThat(tally()).isEqualTo(RatingTally.of(0, 1, 0, 0, 1));
    assertThat(engagement.catalogRating(course)).containsExactly(2, 350);

    assertThat(delete(learner)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(delete(learner)).hasStatus(HttpStatus.NO_CONTENT); // idempotent
    assertThat(tally()).isEqualTo(RatingTally.of(0, 0, 0, 0, 1));
    assertThat(engagement.catalogRating(course)).containsExactly(1, 500);
    assertThat(mvc.get().uri("/api/v1/me/reviews/{c}", course).with(as(learner, Role.LEARNER)))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  /** ⭐ The lock comes first: one learner racing themselves is still ONE review, counted once. */
  @Test
  void twentyConcurrentWritesByOneLearnerCountOnce() throws Exception {
    List<Callable<Object>> writes = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      int rating = i % 5 + 1;
      writes.add(() -> reviews.write(user(learner), course, rating, "take " + rating));
    }
    runAll(writes);

    int stored =
        jdbc.sql("SELECT rating FROM review WHERE course_id = :c")
            .param("c", course)
            .query(Integer.class)
            .single(); // ONE row
    RatingTally expected = RatingTally.empty().add(stored);
    assertThat(tally()).isEqualTo(expected); // the last write's rating, once
    assertThat(engagement.catalogRating(course)).containsExactly(1, stored * 100);
  }

  @Test
  void tenLearnersReviewingAtOnceAreAllCounted() throws Exception {
    List<Callable<Object>> writes = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      UUID someone = enrolledLearner("Learner " + i);
      int rating = i % 2 == 0 ? 5 : 4;
      writes.add(() -> reviews.write(user(someone), course, rating, ""));
    }
    runAll(writes);

    assertThat(tally()).isEqualTo(RatingTally.of(0, 0, 0, 5, 5));
    assertThat(engagement.catalogRating(course)).containsExactly(10, 450);
  }

  @Test
  void onlyEnrolledLearnersWhoDontTeachItMayReview() {
    UUID stranger = new TestInstructors(dataSource).create("Sam Stranger").id();
    assertThat(put(stranger, 5, ""))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("NOT_ENROLLED");
    engagement.enroll(instructor, course); // even enrolled, a teacher can't review their course
    assertThat(put(instructor, 5, ""))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("OWN_COURSE");

    UUID draft = new TestCourses(dataSource).create(instructor, "DRAFT", 0, true, null).courseId();
    engagement.enroll(learner, draft);
    assertThat(
            mvc.put()
                .uri("/api/v1/me/reviews/{c}", draft)
                .with(as(learner, Role.LEARNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 5}"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/v1/courses/{c}/reviews", draft)).hasStatus(HttpStatus.NOT_FOUND);

    assertThat(put(learner, 6, "")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            mvc.put()
                .uri("/api/v1/me/reviews/{c}", course)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 5}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(tally()).isEqualTo(RatingTally.empty());
  }

  /**
   * Archived: its learners still review it; its public page (and reviews) is gone, like catalog's.
   */
  @Test
  void anArchivedCourseIsReviewedByItsLearnersButNotListedPublicly() {
    jdbc.sql("UPDATE course SET status = 'ARCHIVED' WHERE id = :c").param("c", course).update();

    assertThat(put(learner, 5, "")).hasStatusOk();
    assertThat(mvc.get().uri("/api/v1/courses/{c}/reviews", course))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(engagement.catalogRating(course)).containsExactly(1, 500);
  }

  @Test
  void theCoursePageListsReviewsNewestFirstAndPublicly() {
    for (int i = 0; i < 3; i++) {
      UUID someone = enrolledLearner("Learner " + i);
      reviews.write(user(someone), course, 3 + i, "review " + i);
    }

    var first =
        assertThat(mvc.get().uri("/api/v1/courses/{c}/reviews?limit=2", course))
            .hasStatusOk()
            .bodyJson();
    first.extractingPath("$.items[0].body").isEqualTo("review 2");
    first.extractingPath("$.items[1].body").isEqualTo("review 1");
    String cursor =
        TestEngagement.body(mvc.get().uri("/api/v1/courses/{c}/reviews?limit=2", course).exchange())
            .get("nextCursor")
            .asString();
    var second =
        assertThat(mvc.get().uri("/api/v1/courses/{c}/reviews?limit=2&cursor={k}", course, cursor))
            .hasStatusOk()
            .bodyJson();
    second.extractingPath("$.items[0].body").isEqualTo("review 0");
    second.extractingPath("$.nextCursor").isNull();
    assertThat(mvc.get().uri("/api/v1/courses/{c}/reviews?cursor=garbage", course))
        .hasStatus(HttpStatus.BAD_REQUEST);

    var summary =
        assertThat(mvc.get().uri("/api/v1/courses/{c}/reviews/summary", course))
            .hasStatusOk()
            .bodyJson();
    summary.extractingPath("$.count").isEqualTo(3);
    summary.extractingPath("$.average").isEqualTo(4.0);
    summary.extractingPath("$.stars").isEqualTo(List.of(0, 0, 1, 1, 1));
  }

  // --- helpers ---

  private UUID enrolledLearner(String name) {
    UUID id = new TestInstructors(dataSource).create(name).id();
    engagement.enroll(id, course);
    return id;
  }

  private MockMvcTester.MockMvcRequestBuilder put(UUID who, int rating, String body) {
    return mvc.put()
        .uri("/api/v1/me/reviews/{c}", course)
        .with(as(who, Role.LEARNER))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"rating\": %d, \"body\": \"%s\"}".formatted(rating, body));
  }

  private MockMvcTester.MockMvcRequestBuilder delete(UUID who) {
    return mvc.delete().uri("/api/v1/me/reviews/{c}", course).with(as(who, Role.LEARNER));
  }

  private RatingTally tally() {
    return jdbc.sql(
            "SELECT stars_1, stars_2, stars_3, stars_4, stars_5 FROM course_rating"
                + " WHERE course_id = :c")
        .param("c", course)
        .query(
            (rs, n) ->
                RatingTally.of(
                    rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)))
        .optional()
        .orElse(RatingTally.empty());
  }

  private static void runAll(List<Callable<Object>> tasks) throws Exception {
    try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
      for (Future<Object> f : pool.invokeAll(tasks)) {
        f.get(); // ⭐ rethrows: a deadlock or constraint violation fails the test
      }
    }
  }
}
