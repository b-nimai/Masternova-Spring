package com.masternova.api.enrollment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.enrollment.application.EnrollmentService;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.TestCourses.Seeded;
import com.masternova.api.identity.Role;
import java.time.Duration;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Enrollment follows entitlement through a durable, asynchronous module listener (ADR-0021): every
 * grant enrolls, every revoke ends it, in any order, exactly once — with catalog's counter in step.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EnrollmentIT {

  private static final Duration ASYNC = Duration.ofSeconds(10);

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  @Autowired EnrollmentService enrollments;
  JdbcClient jdbc;
  UUID instructor;
  UUID learner;
  UUID admin;
  Seeded paid;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    TestInstructors people = new TestInstructors(dataSource);
    instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    admin = people.create("Ada Admin").id();
    paid = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null);
  }

  @AfterEach
  void clean() {
    awaitListenersDone();
    new TestInstructors(dataSource).deleteAll();
  }

  @Test
  void aGrantEnrollsOnceAndCountsOnce() {
    grant(learner, paid.courseId());
    await().atMost(ASYNC).until(() -> status(learner, paid.courseId()), "ACTIVE"::equals);
    awaitListenersDone();
    grant(learner, paid.courseId()); // already held: no change, no event, no second count

    awaitListenersDone();
    assertThat(enrollmentCount(paid.courseId())).isEqualTo(1);
    assertThat(source(learner, paid.courseId())).isEqualTo("ADMIN_GRANT");
  }

  @Test
  void aRevokeEndsItAndARegrantBringsTheSameEnrollmentBack() {
    grant(learner, paid.courseId());
    await().atMost(ASYNC).until(() -> status(learner, paid.courseId()), "ACTIVE"::equals);
    Instant enrolledAt = enrolledAt(learner, paid.courseId());

    revoke(learner, paid.courseId());
    await().atMost(ASYNC).until(() -> status(learner, paid.courseId()), "REVOKED"::equals);
    awaitListenersDone();
    assertThat(enrollmentCount(paid.courseId())).isZero();

    grant(learner, paid.courseId());
    await().atMost(ASYNC).until(() -> status(learner, paid.courseId()), "ACTIVE"::equals);
    awaitListenersDone();
    assertThat(enrollmentCount(paid.courseId())).isEqualTo(1);
    assertThat(enrolledAt(learner, paid.courseId())) // a re-purchase is not a new learner
        .isEqualTo(enrolledAt);
  }

  @Test
  void eventsAppliedOutOfOrderEndInTheNewestState() {
    Instant t0 = Instant.parse("2026-10-15T10:00:00Z");

    // the refund's event overtakes the purchase's: a tombstone, no count
    assertThat(enrollments.revoked(learner, paid.courseId(), t0.plusSeconds(60))).isFalse();
    assertThat(enrollments.granted(learner, paid.courseId(), EntitlementSource.PURCHASE, t0))
        .isFalse();
    assertThat(status(learner, paid.courseId())).isEqualTo("REVOKED");
    assertThat(enrollmentCount(paid.courseId())).isZero();

    // a re-purchase after the refund is newer: it enrolls
    assertThat(
            enrollments.granted(
                learner, paid.courseId(), EntitlementSource.PURCHASE, t0.plusSeconds(120)))
        .isTrue();
    // and a late copy of the old refund changes nothing
    assertThat(enrollments.revoked(learner, paid.courseId(), t0.plusSeconds(60))).isFalse();
    assertThat(status(learner, paid.courseId())).isEqualTo("ACTIVE");
    assertThat(enrollmentCount(paid.courseId())).isEqualTo(1);
  }

  @Test
  void aFreeCourseEnrollsAtOnceAndIdempotently() {
    Seeded free = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, null);

    for (int i = 0; i < 2; i++) {
      var enrolled = assertThat(enroll(free.courseId())).hasStatusOk().bodyJson();
      enrolled.extractingPath("$.status").isEqualTo("ACTIVE"); // true when the PUT returns
      enrolled.extractingPath("$.courseId").isEqualTo(free.courseId().toString());
    }

    awaitListenersDone(); // the listener's copy of the grant is a no-op
    assertThat(enrollmentCount(free.courseId())).isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT source FROM entitlement WHERE user_id = :u AND course_id = :c")
                .param("u", learner)
                .param("c", free.courseId())
                .query(String.class)
                .single())
        .isEqualTo("FREE_ENROLLMENT");
  }

  @Test
  void onlyAPublishedFreeCourseCanBeEnrolledForFree() {
    Seeded draft = new TestCourses(dataSource).create(instructor, "DRAFT", 0, true, null);
    Seeded unpriced = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, false, null);

    assertThat(enroll(paid.courseId()))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("COURSE_NOT_FREE");
    assertThat(enroll(unpriced.courseId())).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(enroll(draft.courseId())).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(enroll(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.put().uri("/api/v1/me/enrollments/{c}", paid.courseId()))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void aRevokedLearnerCannotReEnrollForFree() {
    Seeded free = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, null);
    assertThat(enroll(free.courseId())).hasStatusOk();
    revoke(learner, free.courseId());

    assertThat(enroll(free.courseId()))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("ENTITLEMENT_REVOKED");
  }

  @Test
  void enrollingForFreeInACourseAlreadyBoughtAnswersTheEnrollment() {
    enrollments.granted(learner, paid.courseId(), EntitlementSource.PURCHASE, Instant.now());

    assertThat(enroll(paid.courseId())) // not 422 COURSE_NOT_FREE
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("ACTIVE");
    assertThat(source(learner, paid.courseId())).isEqualTo("PURCHASE"); // and still bought
  }

  @Test
  void aGrantAfterAnOlderRevokeTombstoneIsEnrolledAtTheGrant() {
    Instant revokedAt = Instant.parse("2026-10-01T10:00:00Z");
    Instant grantedAt = revokedAt.plus(Duration.ofDays(3));
    enrollments.revoked(learner, paid.courseId(), revokedAt); // no row: a tombstone

    assertThat(enrollments.granted(learner, paid.courseId(), EntitlementSource.PURCHASE, grantedAt))
        .isTrue();

    assertThat(enrolledAt(learner, paid.courseId())).isEqualTo(grantedAt); // not the revoke's
  }

  @Test
  void theReconcileRepairsWhatNoListenerSawAndThenChangesNothing() {
    UUID other = new TestInstructors(dataSource).create("Omar Other").id();
    Instant t = Instant.now().minus(Duration.ofHours(1));
    // a pre-Phase-10 pod: a grant with no event, and a revoke with no event
    jdbc.sql(
            "INSERT INTO entitlement (user_id, course_id, source, granted_at)"
                + " VALUES (:u, :c, 'PURCHASE', :t)")
        .param("u", learner)
        .param("c", paid.courseId())
        .param("t", java.sql.Timestamp.from(t))
        .update();
    enrollments.granted(other, paid.courseId(), EntitlementSource.PURCHASE, t);
    jdbc.sql(
            "INSERT INTO entitlement (user_id, course_id, source, granted_at, revoked_at,"
                + " revoke_reason) VALUES (:u, :c, 'PURCHASE', :t, :r, 'refund')")
        .param("u", other)
        .param("c", paid.courseId())
        .param("t", java.sql.Timestamp.from(t))
        .param("r", java.sql.Timestamp.from(t.plusSeconds(60)))
        .update();
    assertThat(enrollmentCount(paid.courseId())).isEqualTo(1); // other's, about to end

    assertThat(enrollments.reconcile()).isEqualTo(2);

    assertThat(status(learner, paid.courseId())).isEqualTo("ACTIVE");
    assertThat(status(other, paid.courseId())).isEqualTo("REVOKED");
    assertThat(enrollmentCount(paid.courseId())).isEqualTo(1); // +1 −1
    assertThat(enrollments.reconcile()).isZero(); // idempotent
    assertThat(enrollmentCount(paid.courseId())).isEqualTo(1);
  }

  // --- helpers ---

  private MockMvcTester.MockMvcRequestBuilder enroll(UUID courseId) {
    return mvc.put().uri("/api/v1/me/enrollments/{c}", courseId).with(as(learner, Role.LEARNER));
  }

  private void grant(UUID userId, UUID courseId) {
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/entitlements/{u}/{c}", userId, courseId)
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.NO_CONTENT);
  }

  private void revoke(UUID userId, UUID courseId) {
    assertThat(
            mvc.delete()
                .uri("/api/v1/admin/entitlements/{u}/{c}?reason=refund", userId, courseId)
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.NO_CONTENT);
  }

  /** Every module-listener publication has completed (completion-mode: delete). */
  private void awaitListenersDone() {
    await()
        .atMost(ASYNC)
        .until(
            () ->
                jdbc.sql("SELECT count(*) FROM event_publication WHERE status <> 'FAILED'")
                    .query(Long.class)
                    .single(),
            n -> n == 0);
  }

  private String status(UUID userId, UUID courseId) {
    return column("status", userId, courseId, String.class);
  }

  private String source(UUID userId, UUID courseId) {
    return column("source", userId, courseId, String.class);
  }

  private Instant enrolledAt(UUID userId, UUID courseId) {
    return column("enrolled_at", userId, courseId, java.sql.Timestamp.class).toInstant();
  }

  private <T> T column(String column, UUID userId, UUID courseId, Class<T> type) {
    return jdbc.sql("SELECT " + column + " FROM enrollment WHERE user_id = :u AND course_id = :c")
        .param("u", userId)
        .param("c", courseId)
        .query(type)
        .optional()
        .orElse(null);
  }

  private int enrollmentCount(UUID courseId) {
    return jdbc.sql("SELECT enrollment_count FROM course WHERE id = :id")
        .param("id", courseId)
        .query(Integer.class)
        .single();
  }

  private static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }
}
