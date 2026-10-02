package com.masternova.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.worker.TestcontainersConfiguration;
import com.masternova.worker.notification.Audience.SuppressionReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The consent queries against the real schema (citext, FK to app_user). */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "masternova.outbox.relay-enabled=false")
@Import(TestcontainersConfiguration.class)
class JdbcAudienceIT {

  @Autowired Audience audience;
  @Autowired JdbcClient jdbc;

  @Test
  void suppressionIsCaseInsensitiveAndKeepsTheFirstReason() {
    String email = "bounce-" + UUID.randomUUID() + "@example.com";

    audience.suppress(email, SuppressionReason.BOUNCED, "550");
    audience.suppress(
        email.toUpperCase(), SuppressionReason.MANUAL, "again"); // no error, no change

    assertThat(audience.isSuppressed(email.toUpperCase())).isTrue();
    assertThat(
            jdbc.sql("SELECT reason FROM email_suppression WHERE email = CAST(:e AS citext)")
                .param("e", email)
                .query(String.class)
                .single())
        .isEqualTo("BOUNCED");
    assertThat(audience.isSuppressed("someone-else@example.com")).isFalse();
  }

  @Test
  void onlyAnExplicitDisabledRowCountsAsAnOptOut() {
    UUID user = insertUser();

    assertThat(audience.hasOptedOut(user, NotificationCategory.PRODUCT_NEWS)).isFalse(); // no row
    setPreference(user, NotificationCategory.PRODUCT_NEWS, false);
    setPreference(user, NotificationCategory.ENGAGEMENT, true);

    assertThat(audience.hasOptedOut(user, NotificationCategory.PRODUCT_NEWS)).isTrue();
    assertThat(audience.hasOptedOut(user, NotificationCategory.ENGAGEMENT)).isFalse();
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO app_user (id, email, display_name, password_hash, created_at, version)
            VALUES (:id, :email, 'Asha', '{noop}x', now(), 0)
            """)
        .param("id", id)
        .param("email", id + "@example.com")
        .update();
    return id;
  }

  private void setPreference(UUID user, NotificationCategory category, boolean enabled) {
    jdbc.sql(
            """
            INSERT INTO notification_preference (user_id, category, enabled, updated_at)
            VALUES (:user, :category, :enabled, :now)
            """)
        .param("user", user)
        .param("category", category.name())
        .param("enabled", enabled)
        .param("now", Timestamp.from(Instant.now()))
        .update();
  }
}
