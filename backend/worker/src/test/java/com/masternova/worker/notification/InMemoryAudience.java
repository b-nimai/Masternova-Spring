package com.masternova.worker.notification;

import com.masternova.kernel.notification.NotificationCategory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A fake {@link Audience} (JdbcAudienceIT proves the SQL). Case-insensitive like citext. */
public final class InMemoryAudience implements Audience {

  public final Map<String, SuppressionReason> suppressed = new HashMap<>();
  public final Set<String> optOuts = new HashSet<>();

  public void optOut(UUID userId, NotificationCategory category) {
    optOuts.add(userId + "/" + category);
  }

  @Override
  public boolean isSuppressed(String email) {
    return suppressed.containsKey(email.toLowerCase(Locale.ROOT));
  }

  @Override
  public boolean hasOptedOut(UUID userId, NotificationCategory category) {
    return optOuts.contains(userId + "/" + category);
  }

  @Override
  public void suppress(String email, SuppressionReason reason, String detail) {
    suppressed.putIfAbsent(email.toLowerCase(Locale.ROOT), reason);
  }
}
