package com.masternova.kernel.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The one HMAC token codec of the project: {@code base64url(payload) "." base64url(HMAC-SHA256)}.
 * Stateless — nothing is stored per token; the signature proves we issued the payload. Typed
 * wrappers ({@code UnsubscribeTokens}, the api's playback tickets) decide what the payload says and
 * when it expires.
 *
 * <p>⭐ One implementation of the security-sensitive part — key length, constant-time comparison,
 * "parse only what we signed" — so a fix lands everywhere at once.
 */
public final class SignedTokens {

  private static final String ALGORITHM = "HmacSHA256";
  private static final int MIN_SECRET_BYTES =
      32; // HMAC-SHA256: a key shorter than the hash is weak
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();

  private final SecretKeySpec key;

  /**
   * @param what names the secret in the error ("the unsubscribe token secret")
   */
  public SignedTokens(String secret, String what) {
    byte[] bytes = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(what + " needs at least " + MIN_SECRET_BYTES + " bytes");
    }
    this.key = new SecretKeySpec(bytes, ALGORITHM);
  }

  public String sign(String payload) {
    String encoded = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encoded + "." + B64.encodeToString(mac(encoded));
  }

  /**
   * @return the payload, or empty if the token is forged or malformed — callers get no hint which
   */
  public Optional<String> verify(String token) {
    if (token == null) {
      return Optional.empty();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.')) {
      return Optional.empty();
    }
    String encoded = token.substring(0, dot);
    try {
      // ⭐ constant-time: equals() returns at the first differing byte, and that timing lets an
      //    attacker guess a valid signature byte by byte
      if (!MessageDigest.isEqual(mac(encoded), B64_DECODE.decode(token.substring(dot + 1)))) {
        return Optional.empty();
      }
      return Optional.of(new String(B64_DECODE.decode(encoded), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) { // bad base64
      return Optional.empty();
    }
  }

  private byte[] mac(String payload) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM); // NOT thread-safe: a fresh instance per call (cheap)
      mac.init(key);
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is required on every JVM", e);
    }
  }
}
