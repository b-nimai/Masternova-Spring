package com.masternova.api.entitlement.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.masternova.api.entitlement.AccessDecision;
import com.masternova.api.entitlement.AccessGrant;
import com.masternova.api.entitlement.DenialReason;

/**
 * {@code {"kind": "ALLOWED", "grant": "PREVIEW_LECTURE"}} or {@code {"kind": "DENIED", "reason":
 * "NO_ENTITLEMENT"}} — a discriminated union, so the client switches on {@code kind} and each
 * branch has exactly its own field (API conventions §10).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AccessResponse.Allowed.class, name = "ALLOWED"),
  @JsonSubTypes.Type(value = AccessResponse.Denied.class, name = "DENIED")
})
public sealed interface AccessResponse {

  record Allowed(AccessGrant grant) implements AccessResponse {}

  record Denied(DenialReason reason) implements AccessResponse {}

  static AccessResponse from(AccessDecision decision) {
    return switch (decision) {
      case AccessDecision.Allowed(AccessGrant grant) -> new Allowed(grant);
      case AccessDecision.Denied(DenialReason reason) -> new Denied(reason);
    };
  }
}
