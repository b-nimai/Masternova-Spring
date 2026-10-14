package com.masternova.api.entitlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.TestCourses.Seeded;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The facade end to end over HTTP: real catalog facts, the real chain, the cached repository on
 * real Postgres + Redis. Every answer is read through the access endpoints the SPA uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntitlementAccessIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  UUID instructor;
  UUID learner;
  UUID admin;
  Seeded paid;

  @BeforeEach
  void setUp() {
    TestInstructors people = new TestInstructors(dataSource);
    instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    admin = people.create("Ada Admin").id();
    paid = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null);
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

  private MockMvcTester.MockMvcRequestBuilder courseAccess(UUID courseId) {
    return mvc.get().uri("/api/v1/courses/{id}/access", courseId);
  }

  private void grant(UUID userId, UUID courseId) {
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/entitlements/{u}/{c}", userId, courseId)
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.NO_CONTENT);
  }

  @Test
  void anAnonymousVisitorSeesOnlyThePreviewUnlocked() {
    var access = assertThat(courseAccess(paid.courseId())).hasStatusOk().bodyJson();
    access.extractingPath("$.course.kind").isEqualTo("DENIED");
    access.extractingPath("$.course.reason").isEqualTo("NO_ENTITLEMENT");
    access.extractingPath("$.lectures[0].lectureId").isEqualTo(paid.previewLecture().toString());
    access.extractingPath("$.lectures[0].access.kind").isEqualTo("ALLOWED");
    access.extractingPath("$.lectures[0].access.grant").isEqualTo("PREVIEW_LECTURE");
    access.extractingPath("$.lectures[1].access.reason").isEqualTo("NO_ENTITLEMENT");
  }

  @Test
  void aGrantOpensTheCourseAndARevokeClosesItEvenThePreview() {
    // first read caches "no row" — the grant must evict it, or the learner stays locked out
    assertThat(courseAccess(paid.courseId()).with(as(learner, Role.LEARNER)))
        .bodyJson()
        .extractingPath("$.course.reason")
        .isEqualTo("NO_ENTITLEMENT");

    grant(learner, paid.courseId());
    assertThat(courseAccess(paid.courseId()).with(as(learner, Role.LEARNER)))
        .bodyJson()
        .extractingPath("$.course.grant")
        .isEqualTo("ENTITLED");

    assertThat(
            mvc.delete()
                .uri("/api/v1/admin/entitlements/{u}/{c}?reason=refund", learner, paid.courseId())
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.NO_CONTENT);
    var revoked =
        assertThat(courseAccess(paid.courseId()).with(as(learner, Role.LEARNER))).bodyJson();
    revoked.extractingPath("$.course.reason").isEqualTo("ENTITLEMENT_REVOKED");
    revoked // ⭐ deny wins: a refund beats the free preview
        .extractingPath("$.lectures[0].access.reason")
        .isEqualTo("ENTITLEMENT_REVOKED");
  }

  @Test
  void aDraftIsTheInstructorsAlone() {
    Seeded draft = new TestCourses(dataSource).create(instructor, "DRAFT", 49_900, true, null);

    assertThat(courseAccess(draft.courseId()).with(as(instructor, Role.INSTRUCTOR)))
        .bodyJson()
        .extractingPath("$.course.grant")
        .isEqualTo("OWNER");
    assertThat(
            mvc.get()
                .uri("/api/v1/lectures/{id}/access", draft.previewLecture())
                .with(as(learner, Role.LEARNER)))
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("COURSE_NOT_PUBLISHED");
  }

  @Test
  void aFreeCourseIsOpenButAnUnpricedOneIsNot() {
    Seeded free = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, true, null);
    Seeded unpriced = new TestCourses(dataSource).create(instructor, "PUBLISHED", 0, false, null);

    assertThat(courseAccess(free.courseId()))
        .bodyJson()
        .extractingPath("$.course.grant")
        .isEqualTo("FREE_COURSE");
    assertThat(courseAccess(unpriced.courseId()))
        .bodyJson()
        .extractingPath("$.course.reason")
        .isEqualTo("NO_ENTITLEMENT");
  }

  @Test
  void supportCanSeeEveryPolicysVote() {
    var explanation =
        assertThat(
                mvc.get()
                    .uri(
                        "/api/v1/admin/entitlements/explain?userId={u}&lectureId={l}",
                        learner,
                        paid.paidLecture())
                    .with(as(admin, Role.ADMIN)))
            .hasStatusOk()
            .bodyJson();
    explanation.extractingPath("$.decision.reason").isEqualTo("NO_ENTITLEMENT");
    explanation.extractingPath("$.votes.length()").isEqualTo(7);
    explanation.extractingPath("$.votes[0].policy").isEqualTo("AdminPolicy");
    explanation.extractingPath("$.votes[0].vote").isEqualTo("ABSTAIN");
  }

  @Test
  void onlyAdminsGrantAndUnknownIdsAre404() {
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/entitlements/{u}/{c}", learner, paid.courseId())
                .with(as(learner, Role.LEARNER)))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.reason")
        .isEqualTo("INSUFFICIENT_ROLE");
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/entitlements/{u}/{c}", learner, UUID.randomUUID())
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/v1/lectures/{id}/access", UUID.randomUUID()))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aRentalMustEndInTheFutureAndARevokeNeedsAReason() {
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/entitlements/{u}/{c}", learner, paid.courseId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\": \"2020-01-01T00:00:00Z\"}")
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            mvc.delete()
                .uri("/api/v1/admin/entitlements/{u}/{c}?reason=", learner, paid.courseId())
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }
}
