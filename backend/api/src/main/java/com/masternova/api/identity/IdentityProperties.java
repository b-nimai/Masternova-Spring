package com.masternova.api.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Identity settings ({@code masternova.identity.*}) — a module owns its own properties record.
 *
 * @param jwtSecret HS256 signing key for access tokens; at least 32 bytes (256 bits). From the
 *     JWT_ACCESS_SECRET environment variable in every real environment.
 * @param accessTokenTtl JWT lifetime (ADR-0006: short — revocation lag is at most this)
 * @param refreshTokenTtl refresh-token lifetime (how long "stay signed in" lasts)
 * @param verificationTokenTtl email-verification link lifetime
 * @param secureCookies send the refresh cookie with the Secure flag (false only for plain-HTTP dev)
 * @param logVerificationLinks log verification links at INFO — a dev convenience; NEVER in
 *     production
 */
@Validated
@ConfigurationProperties(prefix = "masternova.identity")
public record IdentityProperties(
    @NotBlank @Size(min = 32) String jwtSecret,
    @DefaultValue("15m") @NotNull Duration accessTokenTtl,
    @DefaultValue("30d") @NotNull Duration refreshTokenTtl,
    @DefaultValue("24h") @NotNull Duration verificationTokenTtl,
    @DefaultValue("true") boolean secureCookies,
    @DefaultValue("false") boolean logVerificationLinks) {}
