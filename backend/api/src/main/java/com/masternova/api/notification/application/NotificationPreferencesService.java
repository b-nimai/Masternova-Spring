package com.masternova.api.notification.application;

import com.masternova.api.notification.domain.CategoryPreference;
import com.masternova.api.notification.domain.NotificationPreferences;
import com.masternova.api.platform.RuleViolationException;
import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.notification.UnsubscribeTokens;
import com.masternova.kernel.notification.UnsubscribeTokens.Unsubscribe;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read and change a user's email consent — from the settings page, or from an email link. */
@Service
public class NotificationPreferencesService {

  private final NotificationPreferences preferences;
  private final UnsubscribeTokens tokens;
  private final Clock clock;

  NotificationPreferencesService(
      NotificationPreferences preferences, UnsubscribeTokens tokens, Clock clock) {
    this.preferences = preferences;
    this.tokens = tokens;
    this.clock = clock;
  }

  /** EVERY category, in enum order: stored choices over the default (subscribed). */
  public List<CategoryPreference> list(UUID userId) {
    Map<NotificationCategory, Boolean> stored = preferences.storedChoices(userId);
    return Arrays.stream(NotificationCategory.values())
        .map(
            c ->
                new CategoryPreference(
                    c, c.mandatory() || stored.getOrDefault(c, true))) // ⭐ absent = subscribed
        .toList();
  }

  public CategoryPreference set(UUID userId, NotificationCategory category, boolean enabled) {
    if (category.mandatory()) {
      // receipts and security emails are part of the service itself, not marketing
      throw new RuleViolationException(
          "CATEGORY_MANDATORY",
          "%s emails can't be turned off.".formatted(category),
          Map.of("category", category.name()));
    }
    preferences.save(userId, category, enabled, clock.instant());
    return new CategoryPreference(category, enabled);
  }

  /**
   * Opt out via the signed link in an email. Idempotent: clicking twice is fine.
   *
   * @return the category the user just left (so the page can say which)
   */
  public NotificationCategory unsubscribe(String token) {
    Unsubscribe unsubscribe =
        tokens
            .verify(token, clock.instant())
            .filter(u -> !u.category().mandatory()) // the worker never issues these; refuse anyway
            .orElseThrow(
                () ->
                    new RuleViolationException(
                        "UNSUBSCRIBE_TOKEN_INVALID",
                        "This unsubscribe link is invalid or has expired."));
    // a deleted account writes nothing — and the answer stays the same: there's nothing to send
    preferences.save(unsubscribe.userId(), unsubscribe.category(), false, clock.instant());
    return unsubscribe.category();
  }
}
