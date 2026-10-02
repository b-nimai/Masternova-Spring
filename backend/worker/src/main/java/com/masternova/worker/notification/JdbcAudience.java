package com.masternova.worker.notification;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link Audience} over {@code email_suppression} and {@code notification_preference}. */
@Repository
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "ConcreteRepository",
    note = "patterns/docs/16-repository-unit-of-work.md")
class JdbcAudience implements Audience {

  private final JdbcClient jdbc;
  private final Clock clock;

  JdbcAudience(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public boolean isSuppressed(String email) {
    return jdbc.sql(
            // the column is citext, but a text PARAMETER compares case-sensitively: cast it
            "SELECT EXISTS (SELECT 1 FROM email_suppression WHERE email = CAST(:email AS citext))")
        .param("email", email)
        .query(Boolean.class)
        .single();
  }

  @Override
  public boolean hasOptedOut(UUID userId, NotificationCategory category) {
    return jdbc.sql(
            """
            SELECT EXISTS (SELECT 1 FROM notification_preference
                            WHERE user_id = :userId AND category = :category AND NOT enabled)
            """)
        .param("userId", userId)
        .param("category", category.name())
        .query(Boolean.class)
        .single();
  }

  @Override
  public void suppress(String email, SuppressionReason reason, String detail) {
    jdbc.sql(
            """
            INSERT INTO email_suppression (email, reason, detail, created_at)
            VALUES (:email, :reason, :detail, :now)
            ON CONFLICT (email) DO NOTHING
            """)
        .param("email", email)
        .param("reason", reason.name())
        .param("detail", detail)
        .param("now", Timestamp.from(clock.instant()))
        .update();
  }
}
