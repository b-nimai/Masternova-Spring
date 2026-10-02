package com.masternova.api.notification.web;

import com.masternova.api.notification.application.NotificationPreferencesService;
import com.masternova.api.notification.web.dto.UnsubscribeRequest;
import com.masternova.api.notification.web.dto.UnsubscribeResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (the signed token is the credential). Two doors, one rule:
 *
 * <ul>
 *   <li>the Angular {@code /unsubscribe} page POSTs JSON — the link in the email body;
 *   <li>mailbox providers POST {@code List-Unsubscribe=One-Click} as a form (RFC 8058) — the
 *       "Unsubscribe" button Gmail shows next to the sender.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/notifications/unsubscribe")
class UnsubscribeController {

  private final NotificationPreferencesService preferences;

  UnsubscribeController(NotificationPreferencesService preferences) {
    this.preferences = preferences;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  UnsubscribeResponse unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
    return new UnsubscribeResponse(preferences.unsubscribe(request.token()));
  }

  /** RFC 8058: the token is in the URL from the header; the body is the fixed one-click marker. */
  @PostMapping(path = "/one-click", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<Void> oneClick(@RequestParam String token) {
    preferences.unsubscribe(token);
    return ResponseEntity.ok().build();
  }
}
