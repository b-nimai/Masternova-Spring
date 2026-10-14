package com.masternova.api.entitlement.infrastructure;

import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.domain.Entitlement;
import com.masternova.api.entitlement.domain.EntitlementRepository;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The entitlement rows in Postgres — the truth the cache sits in front of. Plain SQL through {@link
 * JdbcClient}: the row is a value (no entity lifecycle to manage), and the grant is one upsert.
 *
 * <p>⭐ It doesn't know it's cached: {@code JdbcEntitlementRepositoryIT} tests the SQL alone.
 */
@DesignPattern(
    value = Pattern.DECORATOR,
    role = "ConcreteComponent: the decorated repository",
    note = "patterns/docs/13-decorator.md")
public final class JdbcEntitlementRepository implements EntitlementRepository {

  private final JdbcClient jdbc;

  public JdbcEntitlementRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Entitlement> find(UUID userId, UUID courseId) {
    return jdbc.sql(
            """
            SELECT user_id, course_id, source, granted_at, expires_at, revoked_at, revoke_reason
            FROM entitlement WHERE user_id = :user AND course_id = :course
            """)
        .param("user", userId)
        .param("course", courseId)
        .query((rs, n) -> map(rs))
        .optional();
  }

  /**
   * ⭐ One statement, so concurrent grants serialise on the primary key instead of racing a
   * read-then-insert. The {@code WHERE} on the update is the "never shorten" rule: only a revoked
   * row, or a rental that the new grant outlasts, is replaced. Anything else updates 0 rows.
   */
  @Override
  public boolean grant(Entitlement grant) {
    return jdbc.sql(
                """
                INSERT INTO entitlement (user_id, course_id, source, granted_at, expires_at)
                VALUES (:user, :course, :source, :grantedAt, :expiresAt)
                ON CONFLICT (user_id, course_id) DO UPDATE
                   SET source = EXCLUDED.source, granted_at = EXCLUDED.granted_at,
                       expires_at = EXCLUDED.expires_at, revoked_at = NULL, revoke_reason = NULL
                 WHERE entitlement.revoked_at IS NOT NULL
                    OR (entitlement.expires_at IS NOT NULL
                        AND (EXCLUDED.expires_at IS NULL
                             OR EXCLUDED.expires_at > entitlement.expires_at))
                """)
            .param("user", grant.userId())
            .param("course", grant.courseId())
            .param("source", grant.source().name())
            .param("grantedAt", Timestamp.from(grant.grantedAt()))
            .param(
                "expiresAt", grant.expiresAt().map(Timestamp::from).orElse(null), Types.TIMESTAMP)
            .update()
        > 0;
  }

  @Override
  public boolean revoke(UUID userId, UUID courseId, Entitlement.Revocation revocation) {
    return jdbc.sql(
                """
                UPDATE entitlement SET revoked_at = :at, revoke_reason = :reason
                WHERE user_id = :user AND course_id = :course AND revoked_at IS NULL
                """)
            .param("at", Timestamp.from(revocation.at()))
            .param("reason", revocation.reason())
            .param("user", userId)
            .param("course", courseId)
            .update()
        > 0;
  }

  private static Entitlement map(ResultSet rs) throws SQLException {
    Timestamp revokedAt = rs.getTimestamp("revoked_at");
    return new Entitlement(
        rs.getObject("user_id", UUID.class),
        rs.getObject("course_id", UUID.class),
        EntitlementSource.valueOf(rs.getString("source")),
        rs.getTimestamp("granted_at").toInstant(),
        Optional.ofNullable(rs.getTimestamp("expires_at")).map(Timestamp::toInstant),
        revokedAt == null
            ? Optional.empty()
            : Optional.of(
                new Entitlement.Revocation(revokedAt.toInstant(), rs.getString("revoke_reason"))));
  }
}
