package com.masternova.api.notification.web;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.notification.application.NotificationPreferencesService;
import com.masternova.api.notification.web.dto.NotificationPreferenceResponse;
import com.masternova.api.notification.web.dto.UpdatePreferenceRequest;
import com.masternova.kernel.notification.NotificationCategory;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's email settings. */
@RestController
@RequestMapping("/api/v1/me/notification-preferences")
class NotificationPreferencesController {

  private final NotificationPreferencesService preferences;

  NotificationPreferencesController(NotificationPreferencesService preferences) {
    this.preferences = preferences;
  }

  @GetMapping
  List<NotificationPreferenceResponse> list(CurrentUser me) {
    return preferences.list(me.id()).stream().map(NotificationPreferenceResponse::from).toList();
  }

  /** PUT: setting a toggle to the same value twice is the same request — idempotent by verb. */
  @PutMapping("/{category}")
  NotificationPreferenceResponse set(
      CurrentUser me,
      @PathVariable NotificationCategory category,
      @Valid @RequestBody UpdatePreferenceRequest request) {
    return NotificationPreferenceResponse.from(
        preferences.set(me.id(), category, request.enabled()));
  }
}
