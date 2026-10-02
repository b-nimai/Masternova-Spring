package com.masternova.api.platform.idempotency;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Idempotency records in Postgres. ⭐ No "read, then decide, then write" races: every state change
 * is ONE conditional statement whose row count says who won.
 */
@Repository
@DesignPattern(
    value = Pattern.REPOSITORY,
    role = "ConcreteRepository",
    note = "patterns/docs/16-repository-unit-of-work.md")
class JdbcIdempotencyStore implements IdempotencyStore {

  private record Row(
      String requestHash,
      String status,
      Instant lockedUntil,
      Instant expiresAt,
      StoredResponse response) {}

  private final JdbcClient jdbc;

  JdbcIdempotencyStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Claim claim(
      String caller,
      String key,
      String requestHash,
      Instant now,
      Instant lockedUntil,
      Instant expiresAt) {
    // 1. ⭐ The primary key arbitrates: exactly ONE concurrent request inserts the row.
    int inserted =
        jdbc.sql(
                """
                INSERT INTO idempotency_record (caller, idem_key, request_hash, status, locked_until, created_at, expires_at)
                VALUES (:caller, :key, :hash, 'IN_PROGRESS', :lockedUntil, :now, :expiresAt)
                ON CONFLICT (caller, idem_key) DO NOTHING
                """)
            .param("caller", caller)
            .param("key", key)
            .param("hash", requestHash)
            .param("lockedUntil", Timestamp.from(lockedUntil))
            .param("now", Timestamp.from(now))
            .param("expiresAt", Timestamp.from(expiresAt))
            .update();
    if (inserted == 1) {
      return new Claim.Acquired();
    }

    Optional<Row> existing = find(caller, key);
    if (existing.isEmpty()) {
      // deleted between our INSERT and SELECT (released after a 5xx) — just try again
      return claim(caller, key, requestHash, now, lockedUntil, expiresAt);
    }
    Row row = existing.get();

    // 2. An EXPIRED key may be reused — take it over, but only if nobody else just did.
    if (row.expiresAt().isBefore(now)) {
      int taken =
          jdbc.sql(
                  """
                  UPDATE idempotency_record
                     SET request_hash = :hash, status = 'IN_PROGRESS', response_status = NULL,
                         response_content_type = NULL, response_body = NULL,
                         locked_until = :lockedUntil, created_at = :now, expires_at = :expiresAt
                   WHERE caller = :caller AND idem_key = :key AND expires_at < :now
                  """)
              .param("hash", requestHash)
              .param("lockedUntil", Timestamp.from(lockedUntil))
              .param("now", Timestamp.from(now))
              .param("expiresAt", Timestamp.from(expiresAt))
              .param("caller", caller)
              .param("key", key)
              .update();
      return taken == 1 ? new Claim.Acquired() : new Claim.InProgress();
    }

    if (!row.requestHash().equals(requestHash)) {
      return new Claim.Mismatch();
    }
    if (row.status().equals("COMPLETED")) {
      return new Claim.Replay(row.response());
    }

    // 3. IN_PROGRESS past its lock: the original request crashed — take it over atomically.
    if (row.lockedUntil().isBefore(now)) {
      int stolen =
          jdbc.sql(
                  """
                  UPDATE idempotency_record SET locked_until = :lockedUntil
                   WHERE caller = :caller AND idem_key = :key AND status = 'IN_PROGRESS' AND locked_until < :now
                  """)
              .param("lockedUntil", Timestamp.from(lockedUntil))
              .param("caller", caller)
              .param("key", key)
              .param("now", Timestamp.from(now))
              .update();
      if (stolen == 1) {
        return new Claim.Acquired();
      }
    }
    return new Claim.InProgress();
  }

  private Optional<Row> find(String caller, String key) {
    return jdbc.sql(
            """
            SELECT request_hash, status, locked_until, expires_at, response_status, response_content_type, response_body
              FROM idempotency_record WHERE caller = :caller AND idem_key = :key
            """)
        .param("caller", caller)
        .param("key", key)
        .query(
            (rs, n) ->
                new Row(
                    rs.getString("request_hash"),
                    rs.getString("status"),
                    rs.getTimestamp("locked_until").toInstant(),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getString("status").equals("COMPLETED")
                        ? new StoredResponse(
                            rs.getInt("response_status"),
                            rs.getString("response_content_type"),
                            rs.getBytes("response_body"))
                        : null))
        .optional();
  }

  @Override
  public void complete(String caller, String key, StoredResponse response) {
    jdbc.sql(
            """
            UPDATE idempotency_record
               SET status = 'COMPLETED', response_status = :status,
                   response_content_type = :contentType, response_body = :body
             WHERE caller = :caller AND idem_key = :key
            """)
        .param("status", response.status())
        .param("contentType", response.contentType())
        .param("body", response.body())
        .param("caller", caller)
        .param("key", key)
        .update();
  }

  @Override
  public void release(String caller, String key) {
    jdbc.sql("DELETE FROM idempotency_record WHERE caller = :caller AND idem_key = :key")
        .param("caller", caller)
        .param("key", key)
        .update();
  }

  @Override
  public int deleteExpired(Instant now) {
    return jdbc.sql("DELETE FROM idempotency_record WHERE expires_at < :now")
        .param("now", Timestamp.from(now))
        .update();
  }
}
