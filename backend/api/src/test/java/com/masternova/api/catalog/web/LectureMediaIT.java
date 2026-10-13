package com.masternova.api.catalog.web;

import static com.masternova.api.catalog.domain.CourseBuilder.aCourse;
import static com.masternova.api.catalog.domain.CourseBuilder.aLecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.CategoryRepository;
import com.masternova.api.catalog.domain.Course;
import com.masternova.api.catalog.domain.CourseRepository;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.catalog.domain.TestMedia;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A lecture gets its video (ATTACH_MEDIA), and the publish gate asks media whether every video is
 * READY (MEDIA_NOT_READY) — catalog and media over HTTP and Postgres, as they meet in Phase 7.8.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LectureMediaIT {

  @Autowired MockMvcTester mvc;
  @Autowired CourseRepository courses;
  @Autowired CategoryRepository categories;
  @Autowired DataSource dataSource;
  JdbcClient db;
  Instructor asha;
  Instructor ravi;
  UUID courseId;
  UUID lectureId;

  @BeforeEach
  void setUp() {
    db = JdbcClient.create(dataSource);
    clean();
    TestInstructors instructors = new TestInstructors(dataSource);
    asha = instructors.create("Asha Rao");
    ravi = instructors.create("Ravi Kumar");
    // three ready videos and one lecture still waiting for its video
    Course course =
        new TestMedia(dataSource)
            .readyVideos(
                aCourse()
                    .by(asha)
                    .in(categories.findBySlug("ci-cd").orElseThrow())
                    .withSection(
                        "Intro",
                        aLecture("Welcome").preview(),
                        aLecture("Setup"),
                        aLecture("Pods"),
                        aLecture("Recorded tomorrow").withoutVideo().seconds(300))
                    .build());
    courseId = courses.save(course).id();
    lectureId = course.sections().getFirst().lectures().getLast().id();
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  private static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  private long version() {
    return db.sql("SELECT version FROM course WHERE id = :id")
        .param("id", courseId)
        .query(Long.class)
        .single();
  }

  private MvcTestResult attach(UUID assetId) {
    return mvc.post()
        .uri("/api/v1/instructor/courses/{id}/curriculum", courseId)
        .with(as(asha.id(), Role.INSTRUCTOR))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"expectedVersion": %d, "command": {"kind": "ATTACH_MEDIA", "lectureId": "%s", "assetId": %s}}
            """
                .formatted(version(), lectureId, assetId == null ? "null" : "\"" + assetId + "\""))
        .exchange();
  }

  private MvcTestResult submit() {
    return mvc.post()
        .uri("/api/v1/instructor/courses/{id}/submit", courseId)
        .with(as(asha.id(), Role.INSTRUCTOR))
        .exchange();
  }

  private UUID asset(Instructor owner, String status, Integer seconds) {
    UUID id = UUID.randomUUID();
    db.sql(
            """
            INSERT INTO media_asset (id, owner_id, kind, status, original_filename, content_type,
                                     size_bytes, duration_seconds, created_at, version)
            VALUES (:id, :owner, 'VIDEO', :status, 'v.mp4', 'video/mp4', 1, :seconds, now(), 0)
            """)
        .param("id", id)
        .param("owner", owner.id())
        .param("status", status)
        .param("seconds", seconds)
        .update();
    return id;
  }

  @Test
  void aVideoLectureWithoutAReadyVideoBlocksSubmitUntilMediaSaysReady() throws Exception {
    assertThat(submit())
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.problems[*].code")
        .asArray()
        .containsExactly("MEDIA_NOT_READY");

    UUID video = asset(asha, "PROCESSING", 512);
    MvcTestResult attached = attach(video);
    assertThat(attached).hasStatusOk();
    assertThat(
            (String)
                JsonPath.read(
                    attached.getResponse().getContentAsString(),
                    "$.sections[0].lectures[3].assetId"))
        .isEqualTo(video.toString()); // the author sees which video a lecture has
    assertThat(submit()).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT); // still transcoding

    db.sql("UPDATE media_asset SET status = 'READY' WHERE id = :id").param("id", video).update();
    assertThat(submit()).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("IN_REVIEW");

    // ⭐ the probed length replaced the instructor's 5-minute estimate
    assertThat(
            db.sql("SELECT duration_seconds FROM lecture WHERE id = :id")
                .param("id", lectureId)
                .query(Integer.class)
                .single())
        .isEqualTo(512);
  }

  @Test
  void someoneElsesVideoCannotBeAttached() throws Exception {
    MvcTestResult result = attach(asset(ravi, "READY", 60));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result.getResponse().getContentAsString()).contains("UNKNOWN_ASSET");
  }

  @Test
  void attachingIsUndoableLikeAnyOtherEdit() {
    attach(asset(asha, "READY", 60)).assertThat().hasStatusOk();

    MvcTestResult undone =
        mvc.post()
            .uri("/api/v1/instructor/courses/{id}/curriculum/undo", courseId)
            .with(as(asha.id(), Role.INSTRUCTOR))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\": %d}".formatted(version()))
            .exchange();

    assertThat(undone).hasStatusOk();
    assertThat(
            db.sql("SELECT asset_id FROM lecture WHERE id = :id")
                .param("id", lectureId)
                .query(UUID.class)
                .optional())
        .isEmpty();
  }
}
