package com.masternova.api.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Opaque tokens for refresh and email verification: 256 random bits, URL-safe. Only the SHA-256
 * HASH is ever stored, so a database leak doesn't hand out working tokens (ADR-0006).
 */
@Component
public class SecureTokens {

  private static final int TOKEN_BYTES = 32; // 256 bits — unguessable
  private final SecureRandom random = new SecureRandom(); // ⭐ SecureRandom, never Random

  /** A new raw token — give it to the client; never store it. */
  public String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * What goes in the database. Unsalted SHA-256 is fine here: the input is already 256 random bits.
   */
  public String hash(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }
}
