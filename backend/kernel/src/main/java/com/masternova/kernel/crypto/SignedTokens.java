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
    return sign(payload, "");
  }

  /**
   * Signs {@code payload} AND {@code boundTo}, but carries only the payload: the token verifies
   * only where the verifier can supply the same {@code boundTo} (e.g. the client IP it was issued
   * to — ADR-0017). ⭐ The bound value never appears in the token, so it never lands in a URL, a log
   * line or a screenshot.
   */
  public String sign(String payload, String boundTo) {
    String encoded = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encoded + "." + B64.encodeToString(mac(encoded, boundTo));
  }

  /**
   * @return the payload, or empty if the token is forged or malformed — callers get no hint which
   */
  public Optional<String> verify(String token) {
    return verify(token, "");
  }

  /**
   * @return the payload, or empty if the token is forged, malformed, or was signed for another
   *     {@code boundTo}
   */
  public Optional<String> verify(String token, String boundTo) {
    if (token == null || boundTo == null) {
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
      if (!MessageDigest.isEqual(
          mac(encoded, boundTo), B64_DECODE.decode(token.substring(dot + 1)))) {
        return Optional.empty();
      }
      return Optional.of(new String(B64_DECODE.decode(encoded), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) { // bad base64
      return Optional.empty();
    }
  }

  /**
   * ⭐ An unbound token MACs exactly the encoded payload, as before: tokens already issued (the
   * unsubscribe links in sent emails) keep verifying. A bound one appends a separator the base64url
   * payload can't contain, so ("ab", "c") and ("a", "bc") can't collide.
   */
  private byte[] mac(String encodedPayload, String boundTo) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM); // NOT thread-safe: a fresh instance per call (cheap)
      mac.init(key);
      mac.update(encodedPayload.getBytes(StandardCharsets.UTF_8));
      if (!boundTo.isEmpty()) {
        mac.update((byte) '\n');
        mac.update(boundTo.getBytes(StandardCharsets.UTF_8));
      }
      return mac.doFinal();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is required on every JVM", e);
    }
  }
}
