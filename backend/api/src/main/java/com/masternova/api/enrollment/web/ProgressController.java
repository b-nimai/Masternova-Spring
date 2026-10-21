package com.masternova.api.enrollment.web;

import com.masternova.api.enrollment.application.ProgressService;
import com.masternova.api.enrollment.web.dto.BeaconRequest;
import com.masternova.api.enrollment.web.dto.HeartbeatRequest;
import com.masternova.api.enrollment.web.dto.HeartbeatResponse;
import com.masternova.api.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Progress from the player (API conventions §15). The heartbeat is a PUT — setting a position is
 * idempotent, and the merge makes repeats harmless. The beacon is a public POST (that's all {@code
 * sendBeacon} can send), authorised by the token in its body.
 *
 * <p>The client IP is {@code getRemoteAddr()}, as for playback tokens (Tomcat resolved it from a
 * trusted proxy's {@code X-Forwarded-For}).
 */
@RestController
class ProgressController {

  private final ProgressService progress;

  ProgressController(ProgressService progress) {
    this.progress = progress;
  }

  @PutMapping("/api/v1/me/progress/{lectureId}")
  HeartbeatResponse heartbeat(
      CurrentUser me,
      @PathVariable UUID lectureId,
      @Valid @RequestBody HeartbeatRequest body,
      HttpServletRequest request) {
    return HeartbeatResponse.from(
        progress.heartbeat(me.id(), lectureId, body.positionSeconds(), request.getRemoteAddr()));
  }

  @PostMapping("/api/v1/progress/beacon")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void beacon(@Valid @RequestBody BeaconRequest body, HttpServletRequest request) {
    progress.beacon(body.token(), body.positionSeconds(), request.getRemoteAddr());
  }
}
