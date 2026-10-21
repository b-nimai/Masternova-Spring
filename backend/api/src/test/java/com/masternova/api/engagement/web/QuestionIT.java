package com.masternova.api.engagement.web;

import static com.masternova.api.engagement.TestEngagement.body;
import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.engagement.TestEngagement;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.TestCourses.Seeded;
import com.masternova.api.identity.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** A lecture's Q&A: a depth-capped tree read in one recursive query, for the course's people. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QuestionIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  JdbcClient jdbc;
  UUID instructor;
  UUID learner;
  Seeded course;
  UUID lecture;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    TestInstructors people = new TestInstructors(dataSource);
    instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, null);
    lecture = course.paidLecture();
    new TestEngagement(dataSource).enroll(learner, course.courseId());
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll(); // courses cascade to questions and answers
  }

  @Test
  void aThreadIsReadDepthFirstInReadingOrder() {
    UUID question = id(ask(learner, "Why does kubectl hang?", UUID.randomUUID().toString()));
    UUID a1 = id(answer(instructor, question, null, "Check your context."));
    UUID a2 = id(answer(learner, question, null, "Same here on Windows."));
    UUID r1 = id(answer(learner, question, a1, "Which context?"));
    UUID r2 = id(answer(instructor, question, r1, "`kubectl config current-context`."));

    assertThat(answer(learner, question, r2, "Too deep"))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("REPLY_TOO_DEEP");

    JsonNode thread = json(mvc.get().uri("/api/v1/questions/{q}", question).with(as(learner)));
    List<String> order = new ArrayList<>();
    List<Integer> depths = new ArrayList<>();
    thread.get("answers").forEach(a -> order.add(a.get("id").asString()));
    thread.get("answers").forEach(a -> depths.add(a.get("depth").asInt()));
    // ⭐ a1, its reply, the reply's reply, THEN a2 — not the order they were posted
    assertThat(order).containsExactly(a1.toString(), r1.toString(), r2.toString(), a2.toString());
    assertThat(depths).containsExactly(1, 2, 3, 1);
    assertThat(thread.get("answers").get(0).get("byInstructor").asBoolean()).isTrue();
    assertThat(thread.get("answers").get(1).get("byInstructor").asBoolean()).isFalse();
    assertThat(thread.get("question").get("answerCount").asInt()).isEqualTo(4);
    assertThat(thread.get("truncated").asBoolean()).isFalse();
  }

  @Test
  void aReplyMustStayInItsOwnThread() {
    UUID mine = id(ask(learner, "First", UUID.randomUUID().toString()));
    UUID other = id(ask(learner, "Second", UUID.randomUUID().toString()));
    UUID answerOfOther = id(answer(learner, other, null, "An answer over there"));

    assertThat(answer(learner, mine, answerOfOther, "Borrowing a parent"))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void onlyTheCoursesPeopleMaySpeak() {
    UUID stranger = new TestInstructors(dataSource).create("Sam Stranger").id();
    UUID admin = new TestInstructors(dataSource).create("Ada Admin").id();

    assertThat(ask(stranger, "Hello?", UUID.randomUUID().toString()))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("NOT_ENROLLED");
    assertThat(mvc.get().uri("/api/v1/lectures/{l}/questions", lecture).with(as(stranger)))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(
            mvc.get()
                .uri("/api/v1/lectures/{l}/questions", lecture)
                .with(TestEngagement.as(admin, Role.ADMIN)))
        .hasStatusOk();
    var byInstructor =
        assertThat(ask(instructor, "Pinned: read this first", UUID.randomUUID().toString()))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson();
    byInstructor.extractingPath("$.byInstructor").isEqualTo(true);

    Seeded draft = new TestCourses(dataSource).create(instructor, "DRAFT", 0, true, null);
    new TestEngagement(dataSource).enroll(learner, draft.courseId());
    assertThat(
            mvc.get().uri("/api/v1/lectures/{l}/questions", draft.paidLecture()).with(as(learner)))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/v1/lectures/{l}/questions", UUID.randomUUID()).with(as(learner)))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/v1/lectures/{l}/questions", lecture))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  /** An archived course keeps playing for those who hold it, so its Q&A stays theirs. */
  @Test
  void anArchivedCoursesQandAStaysOpenToItsLearners() {
    UUID question = id(ask(learner, "Before archiving", UUID.randomUUID().toString()));
    jdbc.sql("UPDATE course SET status = 'ARCHIVED' WHERE id = :c")
        .param("c", course.courseId())
        .update();

    assertThat(mvc.get().uri("/api/v1/lectures/{l}/questions", lecture).with(as(learner)))
        .hasStatusOk();
    assertThat(answer(learner, question, null, "Still here")).hasStatus(HttpStatus.CREATED);
    UUID stranger = new TestInstructors(dataSource).create("Sam Stranger").id();
    assertThat(mvc.get().uri("/api/v1/questions/{q}", question).with(as(stranger)))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void aRetriedPostWithTheSameKeyPostsOnce() {
    String key = UUID.randomUUID().toString();
    UUID first = id(ask(learner, "Is the code on GitHub?", key));
    MvcTestResult retry = ask(learner, "Is the code on GitHub?", key).exchange();

    assertThat(retry).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "true");
    assertThat(body(retry).get("id").asString()).isEqualTo(first.toString());
    assertThat(questionCount()).isEqualTo(1);
    assertThat(
            mvc.post()
                .uri("/api/v1/lectures/{l}/questions", lecture)
                .with(as(learner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"No key\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  /** ⭐ Keyset paging: a question posted while someone pages never duplicates or shifts a page. */
  @Test
  void pagesNeverRepeatAQuestionPostedMeanwhile() {
    for (int i = 0; i < 5; i++) {
      ask(learner, "Question " + i, UUID.randomUUID().toString()).exchange();
    }
    JsonNode first = page(null);
    ask(learner, "Posted while paging", UUID.randomUUID().toString()).exchange();
    JsonNode second = page(first.get("nextCursor").asString());
    JsonNode third = page(second.get("nextCursor").asString());

    List<String> titles = new ArrayList<>();
    for (JsonNode p : List.of(first, second, third)) {
      p.get("items").forEach(q -> titles.add(q.get("title").asString()));
    }
    assertThat(titles)
        .containsExactly(
            "Question 4", "Question 3", "Question 2", "Question 1", "Question 0"); // newest first
    assertThat(third.get("nextCursor").isNull()).isTrue();
  }

  // --- helpers ---

  private MockMvcTester.MockMvcRequestBuilder ask(UUID who, String title, String key) {
    return mvc.post()
        .uri("/api/v1/lectures/{l}/questions", lecture)
        .with(as(who))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\": \"%s\", \"body\": \"\"}".formatted(title));
  }

  private MockMvcTester.MockMvcRequestBuilder answer(
      UUID who, UUID question, UUID parent, String text) {
    String parentJson = parent == null ? "null" : "\"" + parent + "\"";
    return mvc.post()
        .uri("/api/v1/questions/{q}/answers", question)
        .with(as(who))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentAnswerId\": %s, \"body\": \"%s\"}".formatted(parentJson, text));
  }

  private UUID id(MockMvcTester.MockMvcRequestBuilder post) {
    MvcTestResult result = post.exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return UUID.fromString(body(result).get("id").asString());
  }

  private JsonNode page(String cursor) {
    String uri =
        "/api/v1/lectures/"
            + lecture
            + "/questions?limit=2"
            + (cursor == null ? "" : "&cursor=" + cursor);
    return json(mvc.get().uri(uri).with(as(learner)));
  }

  private JsonNode json(MockMvcTester.MockMvcRequestBuilder get) {
    MvcTestResult result = get.exchange();
    assertThat(result).hasStatusOk();
    return body(result);
  }

  private long questionCount() {
    return jdbc.sql("SELECT count(*) FROM question").query(Long.class).single();
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor as(UUID id) {
    return TestEngagement.as(id, Role.LEARNER);
  }
}
