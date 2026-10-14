package com.masternova.api.entitlement.web;

import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.application.EntitlementService;
import com.masternova.api.entitlement.web.dto.ExplanationResponse;
import com.masternova.api.entitlement.web.dto.GrantEntitlementRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Support's tools until commerce grants for real (Phase 9): give or take a course, and see why a
 * learner can't watch. ⭐ PUT and DELETE on the pair: both idempotent by nature, so a retried
 * request needs no Idempotency-Key.
 *
 * <p>The role check is HERE, not on the facade: commerce calls {@code grant} with no user at all.
 */
@RestController
@RequestMapping("/api/v1/admin/entitlements")
@PreAuthorize("hasRole('ADMIN')")
class AdminEntitlementController {

  private final EntitlementService entitlements;

  AdminEntitlementController(EntitlementService entitlements) {
    this.entitlements = entitlements;
  }

  @PutMapping("/{userId}/{courseId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void grant(
      @PathVariable UUID userId,
      @PathVariable UUID courseId,
      @Valid @RequestBody(required = false) GrantEntitlementRequest request) {
    entitlements.grant(
        userId,
        courseId,
        EntitlementSource.ADMIN_GRANT,
        Optional.ofNullable(request).map(GrantEntitlementRequest::expiresAt));
  }

  @DeleteMapping("/{userId}/{courseId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(
      @PathVariable UUID userId,
      @PathVariable UUID courseId,
      @RequestParam @NotBlank @Size(max = 200) String reason) {
    entitlements.revoke(userId, courseId, reason);
  }

  @GetMapping("/explain")
  ExplanationResponse explain(@RequestParam UUID userId, @RequestParam UUID lectureId) {
    return ExplanationResponse.from(entitlements.explain(userId, lectureId));
  }
}
