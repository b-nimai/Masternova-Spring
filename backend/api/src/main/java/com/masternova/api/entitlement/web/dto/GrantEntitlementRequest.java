package com.masternova.api.entitlement.web.dto;

import jakarta.validation.constraints.Future;
import java.time.Instant;

/**
 * An admin's grant. {@code expiresAt} omitted = lifetime access.
 *
 * @param expiresAt when the access ends; must be in the future
 */
public record GrantEntitlementRequest(@Future Instant expiresAt) {}
