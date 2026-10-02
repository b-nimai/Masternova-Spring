package com.masternova.api.notification.domain;

import com.masternova.kernel.notification.NotificationCategory;

/** One category as the user sees it. A mandatory category is always enabled. */
public record CategoryPreference(NotificationCategory category, boolean enabled) {

  public boolean mandatory() {
    return category.mandatory();
  }
}
