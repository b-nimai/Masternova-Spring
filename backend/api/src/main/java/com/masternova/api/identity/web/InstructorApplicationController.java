package com.masternova.api.identity.web;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.application.InstructorApplicationService;
import com.masternova.api.identity.web.dto.ApplyAsInstructorRequest;
import com.masternova.api.identity.web.dto.InstructorApplicationResponse;
import com.masternova.api.platform.NotFoundException;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "I want to teach": the signed-in user's own instructor application. */
@RestController
@RequestMapping("/api/v1/me/instructor-application")
class InstructorApplicationController {

  private final InstructorApplicationService applications;

  InstructorApplicationController(InstructorApplicationService applications) {
    this.applications = applications;
  }

  @GetMapping
  InstructorApplicationResponse mine(CurrentUser me) {
    return applications
        .mine(me)
        .map(InstructorApplicationResponse::from)
        .orElseThrow(() -> new NotFoundException("InstructorApplication", me.id()));
  }

  @PostMapping
  ResponseEntity<InstructorApplicationResponse> apply(
      @Valid @RequestBody ApplyAsInstructorRequest request, CurrentUser me) {
    return ResponseEntity.created(URI.create("/api/v1/me/instructor-application"))
        .body(InstructorApplicationResponse.from(applications.apply(me, request.pitch())));
  }
}
