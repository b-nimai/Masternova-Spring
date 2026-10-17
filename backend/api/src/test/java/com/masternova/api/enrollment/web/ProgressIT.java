package com.masternova.api.enrollment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.enrollment.application.EnrollmentService;
import com.masternova.api.enrollment.infrastructure.WriteBackProgressStore;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.TestCourses.Seeded;
import com.masternova.api.identity.Role;
import java.time.Instant;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The player's whole loop over HTTP (API conventions §15): heartbeat → resume at once (the buffer)
 * → flush → the dashboard's rollups; and the beacon's credential, which works for one lecture from
 * one network.
 */
@SpringBootTest(properties = "masternova.enrollment.flush-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProgressIT {

  static final String IP = "203.0.113.7";
  static final List<String> BUFFER = List.of("progress:v1:dirty", "progress:v1:flushing");

  @Autowired MockMvcTester mvc;
  @Autowired WriteBackProgressStore store;
  @Autowired StringRedisTemplate redis;
  @Autowired EnrollmentService enrollments;
  @Autowired DataSource dataSource;
  UUID learner;
  Seeded course; // a 60 s preview video, then a 60 s paid one

  @BeforeEach
  void setUp() {
    redis.delete(BUFFER);
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null);
    enrollments.granted(
        learner, course.courseId(), EntitlementSource.PURCHASE, Instant.now().minusSeconds(60));
  }

  @AfterEach
  void clean() {
    redis.delete(BUFFER);
    new TestInstructors(dataSource).deleteAll();
  }

  @Test
  void aHeartbeatResumesAtOnceAndReachesTheDashboardAfterTheFlush() {
    assertThat(heartbeat(course.previewLecture(), 55)).hasStatusOk(); // ≥ 90 %: complete
    assertThat(heartbeat(course.paidLecture(), 25)).hasStatusOk();

    var resume = assertThat(get("/api/v1/me/progress/{l}", course.paidLecture())).bodyJson();
    resume.extractingPath("$.positionSeconds").isEqualTo(25);
    resume.extractingPath("$.completed").isEqualTo(false);

    var page = assertThat(get("/api/v1/me/courses/{c}/progress", course.courseId())).bodyJson();
    page.extractingPath("$.enrolled").isEqualTo(true);
    page.extractingPath("$.percentComplete").isEqualTo(50);
    page.extractingPath("$.completedLectureIds")
        .isEqualTo(List.of(course.previewLecture().toString()));
    page.extractingPath("$.continueLectureId").isEqualTo(course.paidLecture().toString());
    page.extractingPath("$.continuePositionSeconds").isEqualTo(25);

    var before = assertThat(get("/api/v1/me/learning")).bodyJson(); // rollups: not flushed yet
    before.extractingPath("$[0].courseId").isEqualTo(course.courseId().toString());
    before.extractingPath("$[0].percentComplete").isEqualTo(0);
    before.extractingPath("$[0].continueLectureId").isEqualTo(course.previewLecture().toString());

    store.flush();

    var after = assertThat(get("/api/v1/me/learning")).bodyJson();
    after.extractingPath("$[0].completedLectures").isEqualTo(1);
    after.extractingPath("$[0].totalLectures").isEqualTo(2);
    after.extractingPath("$[0].percentComplete").isEqualTo(50);
    after.extractingPath("$[0].continueLectureId").isEqualTo(course.paidLecture().toString());
  }

  @Test
  void theBeaconWorksForItsLectureFromItsNetworkWithoutTheBearer() {
    String token =
        assertThat(heartbeat(course.paidLecture(), 10))
            .bodyJson()
            .extractingPath("$.beaconToken")
            .asString()
            .actual();

    assertThat(beacon(token, 40, "198.51.100.9")) // another network
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("BEACON_TOKEN_INVALID");
    assertThat(beacon(token + "x", 40, IP)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(beacon(token, 40, IP)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get("/api/v1/me/progress/{l}", course.paidLecture()))
        .bodyJson()
        .extractingPath("$.positionSeconds")
        .isEqualTo(40);
  }

  @Test
  void theReadsAreTheLearnersOwn() {
    UUID stranger = new TestInstructors(dataSource).create("Sam Stranger").id();
    assertThat(
            mvc.get().uri("/api/v1/me/courses/{c}/progress", course.courseId()).with(as(stranger)))
        .bodyJson()
        .extractingPath("$.enrolled")
        .isEqualTo(false);
    assertThat(mvc.get().uri("/api/v1/me/learning").with(as(stranger)))
        .bodyJson()
        .extractingPath("$.length()")
        .isEqualTo(0);
    assertThat(mvc.get().uri("/api/v1/me/learning")).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(get("/api/v1/me/progress/{l}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aDraftsProgressIsA404ForAnyoneNotEnrolled() {
    UUID instructor = new TestInstructors(dataSource).create("Dev Drafter").id();
    Seeded draft = new TestCourses(dataSource).create(instructor, "DRAFT", 0, true, null);
    assertThat(get("/api/v1/me/courses/{c}/progress", draft.courseId()))
        .hasStatus(HttpStatus.NOT_FOUND); // not "enrolled: false": that would confirm it exists
  }

  @Test
  void aPositionNoPlayerCouldSendIsA400() {
    assertThat(heartbeat(course.paidLecture(), -1)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(heartbeat(course.paidLecture(), 86_401)).hasStatus(HttpStatus.BAD_REQUEST);
  }

  // --- helpers ---

  private MockMvcTester.MockMvcRequestBuilder heartbeat(UUID lecture, int position) {
    return mvc.put()
        .uri("/api/v1/me/progress/{l}", lecture)
        .with(as(learner))
        .with(from(IP))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"positionSeconds\": " + position + "}");
  }

  private MockMvcTester.MockMvcRequestBuilder beacon(String token, int position, String ip) {
    // ⭐ what sendBeacon sends: a JSON Blob and no Authorization header
    return mvc.post()
        .uri("/api/v1/progress/beacon")
        .with(from(ip))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\": \"" + token + "\", \"positionSeconds\": " + position + "}");
  }

  private MockMvcTester.MockMvcRequestBuilder get(String uri, Object... vars) {
    return mvc.get().uri(uri, vars).with(as(learner));
  }

  private static RequestPostProcessor from(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  private static RequestPostProcessor as(UUID id) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(Role.LEARNER.name())))
        .authorities(
            List.<GrantedAuthority>of(new SimpleGrantedAuthority(Role.LEARNER.authority())));
  }
}
