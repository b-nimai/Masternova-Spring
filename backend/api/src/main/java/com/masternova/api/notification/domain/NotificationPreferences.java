package com.masternova.api.notification.domain;

import com.masternova.kernel.notification.NotificationCategory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code notification_preference} table. Rows exist only for categories the user CHANGED —
 * absent means subscribed (docs/lld/notification.md §3), so a new category needs no backfill.
 */
public interface NotificationPreferences {

  /** Only the stored choices; callers fill in the defaults. */
  Map<NotificationCategory, Boolean> storedChoices(UUID userId);

  /**
   * Upsert one choice.
   *
   * @return {@code false} if the user no longer exists (nothing was written)
   */
  boolean save(UUID userId, NotificationCategory category, boolean enabled, Instant now);
}
