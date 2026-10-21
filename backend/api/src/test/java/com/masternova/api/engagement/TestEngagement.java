package com.masternova.api.engagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Engagement's test fixtures: enrollments inserted with plain SQL (engagement only asks {@code
 * EnrollmentApi}, so its tests needn't go through entitlement's events), callers, and catalog's
 * rating columns.
 */
public final class TestEngagement {

  private final JdbcClient jdbc;

  public TestEngagement(DataSource dataSource) {
    this.jdbc = JdbcClient.create(dataSource);
  }

  public void enroll(UUID userId, UUID courseId) {
    jdbc.sql(
            """
            INSERT INTO enrollment (user_id, course_id, source, status, enrolled_at, status_at)
            VALUES (:u, :c, 'FREE_ENROLLMENT', 'ACTIVE', :t, :t)
            """)
        .param("u", userId)
        .param("c", courseId)
        .param("t", Timestamp.from(Instant.now()))
        .update();
  }

  /** {@code [rating_count, rating_average × 100]} as catalog stores them. */
  public List<Integer> catalogRating(UUID courseId) {
    return jdbc.sql(
            "SELECT rating_count, (rating_average * 100)::int AS avg FROM course WHERE id = :id")
        .param("id", courseId)
        .query((rs, n) -> List.of(rs.getInt(1), rs.getInt(2)))
        .single();
  }

  /** The response body as a JSON tree (a cursor to pass back, an id to follow). */
  public static JsonNode body(MvcTestResult result) {
    try {
      return JsonMapper.shared()
          .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  public static CurrentUser user(UUID id, Role... roles) {
    return new CurrentUser(id, Set.of(roles.length == 0 ? new Role[] {Role.LEARNER} : roles), true);
  }

  public static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }
}
