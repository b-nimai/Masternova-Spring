package com.masternova.api.catalog.web;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.CategoryRepository;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.identity.Role;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Undo/redo over HTTP, with the history in Postgres (ADR-0011). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UndoRedoIT {

  @Autowired MockMvcTester mvc;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;
  @Autowired DataSource dataSource;
  @Autowired JsonMapper json;

  Instructor asha;
  UUID courseId;
  JdbcClient jdbc;

  @BeforeEach
  void seedACourse() {
    clean();
    jdbc = JdbcClient.create(dataSource);
    asha = new TestInstructors(dataSource).create("Asha Rao");
    courseId =
        courses
            .save(
                aCourse()
                    .by(asha)
                    .in(categories.findBySlug("ci-cd").orElseThrow())
                    .withSection("Intro", aLecture("Welcome").preview(), aLecture("Setup"))
                    .withSection("Core", aLecture("Pods"), aLecture("Services"))
                    .build())
            .id();
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll(); // course_edit rows cascade with the course
  }

  private MvcTestResult post(String path, String body) {
    return mvc.post()
        .uri("/api/v1/instructor/courses/{id}/curriculum" + path, courseId)
        .with(
            jwt()
                .jwt(j -> j.subject(asha.id().toString()).claim("roles", List.of("INSTRUCTOR")))
                .authorities(
                    List.<GrantedAuthority>of(
                        new SimpleGrantedAuthority(Role.INSTRUCTOR.authority()))))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private JsonNode ok(MvcTestResult result) throws Exception {
    assertThat(result).hasStatusOk();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode command(long version, String command) throws Exception {
    return ok(post("", "{\"expectedVersion\":%d,\"command\":%s}".formatted(version, command)));
  }

  private MvcTestResult undo(long version) {
    return post("/undo", "{\"expectedVersion\":" + version + "}");
  }

  private MvcTestResult redo(long version) {
    return post("/redo", "{\"expectedVersion\":" + version + "}");
  }

  /** Section and lecture ids as stored, in order — the identity undo must preserve. */
  private List<String> storedIds() {
    return jdbc.sql(
            """
            SELECT s.id || '/' || coalesce(l.id::text, '-') FROM section s
              LEFT JOIN lecture l ON l.section_id = s.id
             WHERE s.course_id = :id ORDER BY s.position, l.position
            """)
        .param("id", courseId)
        .query(String.class)
        .list();
  }

  private String coreId() {
    return jdbc.sql("SELECT id FROM section WHERE course_id = :id AND title = 'Core'")
        .param("id", courseId)
        .query(String.class)
        .single();
  }

  /** ⭐ The inverse is a Memento: undoing a removal brings back the SAME section and lecture ids. */
  @Test
  void undoBringsBackARemovedSectionWithItsLecturesAndIds() throws Exception {
    List<String> before = storedIds();

    JsonNode removed =
        command(0, "{\"kind\":\"REMOVE_SECTION\",\"sectionId\":\"" + coreId() + "\"}");
    assertThat(removed.get("canUndo").asBoolean()).isTrue();

    JsonNode undone = ok(undo(1));
    assertThat(storedIds()).isEqualTo(before);
    assertThat(undone.get("version").asLong()).isEqualTo(2);
    assertThat(undone.get("canUndo").asBoolean()).isFalse();
    assertThat(undone.get("canRedo").asBoolean()).isTrue();

    JsonNode redone = ok(redo(2));
    assertThat(redone.get("sections").size()).isEqualTo(1); // removed again
    assertThat(redone.get("canRedo").asBoolean()).isFalse();
  }

  @Test
  void undoAndRedoWalkSeveralEditsInOrder() throws Exception {
    command(0, "{\"kind\":\"ADD_SECTION\",\"title\":\"Bonus\"}");
    command(
        1, "{\"kind\":\"RENAME_SECTION\",\"sectionId\":\"" + coreId() + "\",\"title\":\"Main\"}");

    ok(undo(2)); // un-rename
    assertThat(storedIds()).hasSize(5); // Intro(2) + Core(2) + Bonus(empty)
    JsonNode afterTwoUndos = ok(undo(3)); // un-add
    assertThat(afterTwoUndos.get("sections").size()).isEqualTo(2);

    JsonNode redone = ok(redo(4)); // re-add Bonus — the SAME section id, so…
    JsonNode renamedAgain = ok(redo(5)); // …the rename's redo still finds Core
    assertThat(redone.get("sections").size()).isEqualTo(3);
    assertThat(renamedAgain.get("sections").get(1).get("title").asString()).isEqualTo("Main");
  }

  @Test
  void aNewEditAfterUndoDiscardsTheRedoBranch() throws Exception {
    command(0, "{\"kind\":\"ADD_SECTION\",\"title\":\"Bonus\"}");
    ok(undo(1));

    JsonNode fresh = command(2, "{\"kind\":\"ADD_SECTION\",\"title\":\"Other\"}");

    assertThat(fresh.get("canRedo").asBoolean()).isFalse();
    assertThat(redo(3))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("NOTHING_TO_REDO");
  }

  @Test
  void undoWithNothingToUndoIsAConflict() {
    assertThat(undo(0))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("NOTHING_TO_UNDO");
  }

  /** ⭐ A double-tapped Ctrl+Z: both presses carry version 1; only one undo happens. */
  @Test
  void aDoubleTappedUndoUndoesOnce() throws Exception {
    command(0, "{\"kind\":\"ADD_SECTION\",\"title\":\"One\"}");
    command(1, "{\"kind\":\"ADD_SECTION\",\"title\":\"Two\"}");

    assertThat(undo(2)).hasStatusOk();
    assertThat(undo(2))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VERSION_CONFLICT");
    assertThat(storedIds()).hasSize(5); // "One" is still there: exactly one edit was undone
  }

  @Test
  void undoingAMovePutsTheLectureBackInItsPlace() throws Exception {
    List<String> before = storedIds();
    String pods =
        jdbc.sql("SELECT id FROM lecture WHERE title = 'Pods'").query(String.class).single();
    String intro =
        jdbc.sql("SELECT id FROM section WHERE course_id = :id AND title = 'Intro'")
            .param("id", courseId)
            .query(String.class)
            .single();
    command(
        0,
        "{\"kind\":\"MOVE_LECTURE\",\"lectureId\":\"%s\",\"toSectionId\":\"%s\",\"toPosition\":0}"
            .formatted(pods, intro));

    ok(undo(1));

    assertThat(storedIds()).isEqualTo(before);
  }
}
