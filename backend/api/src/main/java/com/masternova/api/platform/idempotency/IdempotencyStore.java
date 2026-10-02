package com.masternova.api.platform.idempotency;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.time.Instant;

/** Persistence for idempotency keys — every operation is atomic per (caller, key). */
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "Repository",
    note = "patterns/docs/16-repository-unit-of-work.md")
interface IdempotencyStore {

  /** A response worth replaying. */
  record StoredResponse(int status, String contentType, byte[] body) {}

  /** ⭐ Every possible outcome of a claim — a sealed type, so the filter's switch is exhaustive. */
  sealed interface Claim {
    /** This request owns the key: run it, then complete() or release(). */
    record Acquired() implements Claim {}

    /** Same key, same request, already finished: send the stored response again. */
    record Replay(StoredResponse response) implements Claim {}

    /** Same key, same request, still running elsewhere: 409, retry shortly. */
    record InProgress() implements Claim {}

    /** Same key, DIFFERENT request body: a client bug — 422. */
    record Mismatch() implements Claim {}
  }

  Claim claim(
      String caller,
      String key,
      String requestHash,
      Instant now,
      Instant lockedUntil,
      Instant expiresAt);

  void complete(String caller, String key, StoredResponse response);

  /** Forget the key (the request failed with a 5xx) so the client can retry for real. */
  void release(String caller, String key);

  int deleteExpired(Instant now);
}
