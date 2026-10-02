package com.masternova.worker.notification;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.UUID;

/**
 * Who may receive what (docs/lld/notification.md §3): the suppression list and the users'
 * preferences. Behind an interface so the RULES in {@link NotificationService} — "suppression
 * outranks even a mandatory email", "mandatory ignores preferences" — are unit-tested without
 * Postgres.
 */
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "Repository",
    note = "patterns/docs/16-repository-unit-of-work.md")
public interface Audience {

  /** Why an address is on the suppression list (matches the table's CHECK constraint). */
  enum SuppressionReason {
    BOUNCED,
    COMPLAINED,
    MANUAL
  }

  /** Case-insensitive: {@code Asha@Example.com} and {@code asha@example.com} are one mailbox. */
  boolean isSuppressed(String email);

  /** {@code true} only for an explicit opt-out row — no row means subscribed. */
  boolean hasOptedOut(UUID userId, NotificationCategory category);

  /** Idempotent: the first reason recorded for an address is kept. */
  void suppress(String email, SuppressionReason reason, String detail);
}
