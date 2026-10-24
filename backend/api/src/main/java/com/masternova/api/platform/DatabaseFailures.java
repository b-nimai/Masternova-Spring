package com.masternova.api.platform;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import java.util.Set;

/**
 * Which database failures are the database being busy or gone, not a bug, and what the client is
 * told (D5.1, D5.3): the ONE policy for {@code GlobalExceptionHandler} (controllers) and the
 * servlet filters that reach the database before Spring MVC does (idempotency). Part of the
 * platform module's public API, like {@link ProblemTypes}.
 */
public final class DatabaseFailures {

  /**
   * Connection exceptions and server shutdown, as Postgres reports them during a failover or a
   * restart. Exact states, not prefixes: 08P01 (protocol violation) or 57P04 (database dropped) is
   * a bug or a misconfiguration, and a "retry shortly" would hide it.
   */
  private static final Set<String> UNAVAILABLE_STATES =
      Set.of("08000", "08001", "08003", "08004", "08006", "57P01", "57P02", "57P03");

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  /**
   * What to answer: always a 503 with this {@code code} and {@code detail}; {@code Retry-After}
   * only when {@code retryable}.
   */
  public record Unavailable(String code, String detail, boolean retryable) {}

  private DatabaseFailures() {}

  /**
   * {@code DATABASE_UNAVAILABLE}: the database went away (it wins over a pool timeout: Hikari's
   * timeout carries the connection error as its cause while the database is down); {@code
   * OVERLOADED}: no pooled connection in time; empty: anything else, which is a bug.
   *
   * <p>⭐ Retryable: always for {@code OVERLOADED} (nothing reached the database), for {@code
   * DATABASE_UNAVAILABLE} only on a safe method. A write's connection may have died AFTER its
   * COMMIT reached Postgres, so its outcome is unknown, and the idempotency key was released with
   * the 5xx: inviting a retry could apply it twice.
   */
  public static Optional<Unavailable> classify(Throwable ex, String method) {
    boolean poolTimeout = false;
    for (Throwable t = ex; t != null; t = t.getCause()) {
      String state = t instanceof SQLException sql ? sql.getSQLState() : null;
      if (state != null && UNAVAILABLE_STATES.contains(state)) {
        return Optional.of(
            SAFE_METHODS.contains(method)
                ? new Unavailable(
                    "DATABASE_UNAVAILABLE", "The database is unavailable. Retry shortly.", true)
                : new Unavailable(
                    "DATABASE_UNAVAILABLE",
                    "The database became unavailable during the request; it may or may not have"
                        + " been applied. Check before retrying.",
                    false));
      }
      poolTimeout |= t instanceof SQLTransientConnectionException;
    }
    return poolTimeout
        ? Optional.of(new Unavailable("OVERLOADED", "The service is busy. Retry shortly.", true))
        : Optional.empty();
  }
}
