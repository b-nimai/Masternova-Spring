package com.masternova.api.notification.infrastructure;

import com.masternova.api.notification.domain.NotificationPreferences;
import com.masternova.kernel.notification.NotificationCategory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL instead of JPA: the table is a key → boolean map, and the write is a single-statement
 * upsert — no entity, no read-modify-write race between two tabs toggling at once.
 */
@Repository
class JdbcNotificationPreferences implements NotificationPreferences {

  private final JdbcClient jdbc;

  JdbcNotificationPreferences(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<NotificationCategory, Boolean> storedChoices(UUID userId) {
    Map<NotificationCategory, Boolean> choices = new EnumMap<>(NotificationCategory.class);
    List<Map.Entry<String, Boolean>> rows =
        jdbc.sql("SELECT category, enabled FROM notification_preference WHERE user_id = :userId")
            .param("userId", userId)
            .query((rs, n) -> Map.entry(rs.getString("category"), rs.getBoolean("enabled")))
            .list();
    for (Map.Entry<String, Boolean> row : rows) {
      // a category removed from the enum may still have rows: skip it, don't crash
      Arrays.stream(NotificationCategory.values())
          .filter(c -> c.name().equals(row.getKey()))
          .findFirst()
          .ifPresent(c -> choices.put(c, row.getValue()));
    }
    return choices;
  }

  @Override
  public boolean save(UUID userId, NotificationCategory category, boolean enabled, Instant now) {
    // ⭐ INSERT … SELECT … WHERE EXISTS: an unsubscribe link outliving its account writes nothing
    //    (instead of failing on the foreign key); ON CONFLICT makes it an upsert
    int rows =
        jdbc.sql(
                """
                INSERT INTO notification_preference (user_id, category, enabled, updated_at)
                SELECT :userId, :category, :enabled, :now
                 WHERE EXISTS (SELECT 1 FROM app_user WHERE id = :userId)
                ON CONFLICT (user_id, category)
                DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                """)
            .param("userId", userId)
            .param("category", category.name())
            .param("enabled", enabled)
            .param("now", Timestamp.from(now))
            .update();
    return rows == 1;
  }
}
