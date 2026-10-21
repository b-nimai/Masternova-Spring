package com.masternova.api.platform;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The wire format of every opaque cursor (API conventions §2): base64url of {@code |}-joined parts,
 * and the one 400 a client gets for anything this API didn't issue. Each cursor type parses its own
 * typed parts (catalog's sort + key + id, engagement's time + id).
 */
public final class CursorTokens {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private CursorTokens() {}

  public static String encode(String... parts) {
    return ENCODER.encodeToString(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @throws ValidationException (400 {@code INVALID_CURSOR}) for anything that isn't base64url of
   *     exactly {@code count} parts
   */
  public static String[] decode(String token, int count) {
    try {
      String[] parts = new String(DECODER.decode(token), StandardCharsets.UTF_8).split("\\|", -1);
      if (parts.length != count) {
        throw invalid();
      }
      return parts;
    } catch (IllegalArgumentException e) {
      throw invalid();
    }
  }

  /** The client restarts from page 1 (API conventions §2). */
  public static ValidationException invalid() {
    return ValidationException.of(
        "cursor", "INVALID_CURSOR", "The cursor is not valid for this list; start again.");
  }
}
