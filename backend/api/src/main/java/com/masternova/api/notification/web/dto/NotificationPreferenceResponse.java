package com.masternova.api.notification.web.dto;

import com.masternova.api.notification.domain.CategoryPreference;
import com.masternova.kernel.notification.NotificationCategory;

/** One row of the settings page. {@code mandatory} rows render as locked toggles. */
public record NotificationPreferenceResponse(
    NotificationCategory category, boolean enabled, boolean mandatory) {

  public static NotificationPreferenceResponse from(CategoryPreference p) {
    return new NotificationPreferenceResponse(p.category(), p.enabled(), p.mandatory());
  }
}
