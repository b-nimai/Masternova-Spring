package com.masternova.api.identity.infrastructure;

import com.masternova.api.identity.IdentityProperties;
import com.masternova.api.identity.Role;
import com.masternova.api.identity.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Mints the short-lived JWT access token (ADR-0006). */
@Component
public class AccessTokenIssuer {

  /** The token and when it expires (the client refreshes shortly before). */
  public record AccessToken(String value, Instant expiresAt) {}

  private final JwtEncoder encoder;
  private final IdentityProperties settings;

  AccessTokenIssuer(JwtEncoder encoder, IdentityProperties settings) {
    this.encoder = encoder;
    this.settings = settings;
  }

  public AccessToken issue(User user, UUID sessionId, Instant now) {
    Instant expiresAt = now.plus(settings.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("masternova")
            .subject(user.id().toString()) // ⭐ WHO — the user id, never the email (emails change)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(
                "roles",
                user.roles().stream().map(Role::name).sorted().toList()) // WHAT they may do
            .claim("email_verified", user.isEmailVerified())
            .claim("sid", sessionId.toString()) // which login/device — for logout & session listing
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return new AccessToken(
        encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
  }
}
